package bassamalim.halala.features.assistant

import bassamalim.halala.core.Globals
import bassamalim.halala.core.ai.Ask
import bassamalim.halala.core.ai.AskTool
import bassamalim.halala.core.ai.IdentifyFailure
import bassamalim.halala.core.ai.IdentifyProblem
import bassamalim.halala.core.ai.QuestionReader
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.data.repositories.ForecastRepository
import bassamalim.halala.core.data.repositories.LoansRepository
import bassamalim.halala.core.data.repositories.PeopleRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.domain.Affordability
import bassamalim.halala.core.domain.Answers
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.Owed
import bassamalim.halala.core.domain.SpendingAnswer
import bassamalim.halala.core.domain.Topic
import kotlinx.coroutines.flow.first
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Why a question got no answer. */
enum class AskProblem { NO_KEY, OFFLINE, LIMITED, UNREADABLE, UNSUPPORTED, NO_AMOUNT }

/** What a question found, before it is worded. Amounts in [Globals.PRIMARY_CURRENCY], minor units. */
sealed interface Found {
    data class Spending(val topic: Topic, val from: LocalDate, val to: LocalDate, val answer: SpendingAnswer) : Found
    data class Income(val from: LocalDate, val to: LocalDate, val totalMinor: Long, val count: Int) : Found
    data class Bills(val from: LocalDate, val to: LocalDate, val rows: List<Pair<String, Long>>) : Found
    data class Owing(val toYou: List<Owed>, val byYou: List<Owed>, val names: Map<Long, String>) : Found
    data class Afford(val amountMinor: Long, val on: LocalDate, val monthly: Boolean, val result: Affordability) : Found
    data class Balance(val totalMinor: Long, val accounts: Int) : Found
    data class Problem(val problem: AskProblem) : Found
}

/**
 * The assistant: the AI reads the question into a query ([QuestionReader]); everything after
 * that, finding and adding up, happens here on the phone, with nothing sent back.
 */
class AssistantDomain @Inject constructor(
    private val reader: QuestionReader,
    private val transactions: TransactionsRepository,
    private val classification: ClassificationRepository,
    private val loans: LoansRepository,
    private val people: PeopleRepository,
    private val forecast: ForecastRepository,
    private val accounts: AccountsRepository,
    private val clock: Clock
) {

    fun today(): LocalDate = LocalDate.now(clock)

    fun zone(): ZoneId = clock.zone

    suspend fun answer(question: String): Found {
        val today = today()
        val ask = try {
            reader.read(question, today)
        } catch (failure: IdentifyFailure) {
            return Found.Problem(
                when (failure.problem) {
                    IdentifyProblem.KEY -> AskProblem.NO_KEY
                    IdentifyProblem.UNREACHABLE -> AskProblem.OFFLINE
                    IdentifyProblem.LIMITED -> AskProblem.LIMITED
                    IdentifyProblem.REJECTED -> AskProblem.UNREADABLE
                }
            )
        }
        return run(ask, today)
    }

    suspend fun run(ask: Ask, today: LocalDate): Found {
        val zone = zone()
        val currency = Globals.PRIMARY_CURRENCY
        val (from, to) = periodOf(ask, today)
        return when (ask.tool) {
            AskTool.SPENDING -> {
                val topic = Answers.topicOf(ask.topic, ask.businessType, classification.getCategories(), classification.getMerchants())
                Found.Spending(topic, from, to, Answers.spending(transactions.observeAll().first(), topic, from, to, zone, currency))
            }
            AskTool.INCOME -> Answers.income(transactions.observeAll().first(), from, to, zone, currency)
                .let { (total, count) -> Found.Income(from, to, total, count) }
            AskTool.BILLS -> {
                // "Biggest bills" with no days named means this year.
                val since = ask.from ?: today.withDayOfYear(1)
                Found.Bills(since, to, Answers.bills(transactions.observeAll().first(), since, to, zone, currency))
            }
            AskTool.OWED -> {
                val (toYou, byYou) = Answers.owed(loans.observeStates().first(), currency, zone)
                Found.Owing(toYou, byYou, people.getPeople().associate { it.id to it.name })
            }
            AskTool.AFFORD -> {
                val amount = ask.amount?.setScale(Money.fractionDigits(currency), RoundingMode.HALF_UP)?.unscaledValue()
                    ?.takeIf { it.bitLength() < Long.SIZE_BITS }?.toLong()
                    ?: return Found.Problem(AskProblem.NO_AMOUNT)
                val on = ask.on ?: today
                Found.Afford(amount, on, ask.monthly, Answers.afford(forecast.observeInputs(currency).first(), amount, on, ask.monthly))
            }
            AskTool.BALANCE -> {
                val held = accounts.getAllWithBalance().filter { !it.account.archived && it.account.currency == currency }
                Found.Balance(Money.sum(held.map { it.balanceMinor }), held.size)
            }
            AskTool.UNSUPPORTED -> Found.Problem(AskProblem.UNSUPPORTED)
        }
    }

    companion object {
        /** The days asked about: this month when none were named; never past today, never backwards. */
        fun periodOf(ask: Ask, today: LocalDate): Pair<LocalDate, LocalDate> {
            val to = ask.to?.takeIf { !it.isAfter(today) } ?: today
            val from = ask.from?.takeIf { !it.isAfter(to) } ?: to.withDayOfMonth(1)
            return from to to
        }
    }
}
