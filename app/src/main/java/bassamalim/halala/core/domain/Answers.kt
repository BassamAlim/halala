package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.Category
import bassamalim.halala.core.data.dataSources.room.entities.Merchant
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.enums.BusinessType
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.LoanDirection
import bassamalim.halala.core.enums.TransactionKind
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** What a question's spending is about: a category of yours, merchants, or a kind of business. Empty for all of it. */
sealed interface Topic {
    data object All : Topic
    data class InCategory(val category: Category) : Topic
    data class AtMerchants(val name: String, val ids: Set<Long>) : Topic
    data class OfType(val type: BusinessType, val merchantIds: Set<Long>) : Topic
    /** Asked about something nothing of yours matches. */
    data class Unknown(val words: String) : Topic
}

data class SpendingAnswer(
    val totalMinor: Long,
    val count: Int,
    /** Each month from the first asked about to the last, oldest first. */
    val months: List<Pair<YearMonth, Long>>,
    /** The merchant with the most of it, and its share in whole percent; null with fewer than two merchants. */
    val top: Pair<String, Int>?,
    /** The month with the most, when there are several. */
    val highest: YearMonth?,
    val ids: List<Long>
)

/** Who owes what, by person: what they owe you, and what you owe them, biggest first. */
data class Owed(val personId: Long, val amountMinor: Long, val dueOn: LocalDate?, val since: LocalDate?)

/**
 * The assistant's queries, run on the phone: the AI only said which one and over what. Amounts
 * in one currency, minor units; spending is your share, as in totals and budgets.
 */
object Answers {

    private fun words(text: String) = text.lowercase().trim().removeSuffix("s")

    /** Your words for a topic, matched against your categories first, then merchants, then the AI's business type. */
    fun topicOf(topic: String?, type: BusinessType?, categories: List<Category>, merchants: List<Merchant>): Topic {
        if (topic.isNullOrBlank() && type == null) return Topic.All
        if (!topic.isNullOrBlank()) {
            val asked = words(topic)
            categories.firstOrNull { words(it.name) == asked }
                ?.let { return Topic.InCategory(it) }
            categories.firstOrNull { words(it.name).contains(asked) || asked.contains(words(it.name)) }
                ?.let { return Topic.InCategory(it) }
            val key = Merchants.key(topic)
            val named = merchants.filter { words(it.name) == asked || (key.isNotEmpty() && Merchants.key(it.name) == key) }
            if (named.isNotEmpty()) return Topic.AtMerchants(named.first().name, named.map { it.id }.toSet())
        }
        if (type != null) return Topic.OfType(type, merchants.filter { it.businessType == type }.map { it.id }.toSet())
        return Topic.Unknown(topic.orEmpty())
    }

    fun matches(detail: TransactionDetail, topic: Topic): Boolean = when (topic) {
        Topic.All -> true
        is Topic.InCategory -> detail.transaction.categoryId == topic.category.id
        is Topic.AtMerchants -> detail.merchantId in topic.ids
        is Topic.OfType -> detail.merchantId in topic.merchantIds
        is Topic.Unknown -> false
    }

    private fun day(detail: TransactionDetail, zone: ZoneId) = detail.transaction.occurredAt.atZone(zone).toLocalDate()

    private fun within(detail: TransactionDetail, from: LocalDate, to: LocalDate, zone: ZoneId) =
        day(detail, zone).let { !it.isBefore(from) && !it.isAfter(to) }

    fun spending(details: List<TransactionDetail>, topic: Topic, from: LocalDate, to: LocalDate, zone: ZoneId, currency: String): SpendingAnswer {
        val spent = details.filter {
            Budgets.isSpending(it) && it.transaction.currency == currency && within(it, from, to, zone) && matches(it, topic)
        }
        val byMonth = spent.groupBy { YearMonth.from(day(it, zone)) }.mapValues { (_, list) -> Money.sum(list.map { it.yourMinor }) }
        val months = generateSequence(YearMonth.from(from)) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(YearMonth.from(to)) }
            .map { it to (byMonth[it] ?: 0L) }
            .toList()
        val total = Money.sum(spent.map { it.yourMinor })
        val byMerchant = spent.filter { it.merchantName != null }.groupBy { it.merchantName!! }
            .mapValues { (_, list) -> Money.sum(list.map { it.yourMinor }) }
        val top = byMerchant.maxByOrNull { it.value }
            ?.takeIf { byMerchant.size > 1 && total > 0 }
            ?.let { it.key to (it.value * 100 / total).toInt() }
        return SpendingAnswer(
            totalMinor = total,
            count = spent.size,
            months = months,
            top = top,
            highest = months.takeIf { it.size > 1 && total > 0 }?.maxByOrNull { it.second }?.first,
            ids = spent.sortedByDescending { it.transaction.occurredAt }.map { it.transaction.id }
        )
    }

    /** Money that came in and counts as income, and how many times. */
    fun income(details: List<TransactionDetail>, from: LocalDate, to: LocalDate, zone: ZoneId, currency: String): Pair<Long, Int> {
        val credits = details.filter {
            it.transaction.direction == Direction.CREDIT && it.transaction.kind.countsInTotals && !it.isInternalTransfer &&
                it.transaction.currency == currency && within(it, from, to, zone)
        }
        return Money.sum(credits.map { it.transaction.amountMinor }) to credits.size
    }

    /** The biggest bills paid, by who was paid, biggest first. */
    fun bills(details: List<TransactionDetail>, from: LocalDate, to: LocalDate, zone: ZoneId, currency: String, count: Int = 5): List<Pair<String, Long>> =
        details.filter {
            it.transaction.kind == TransactionKind.BILL_PAYMENT && it.transaction.direction == Direction.DEBIT &&
                it.transaction.currency == currency && within(it, from, to, zone)
        }
            .groupBy { it.merchantName ?: it.transaction.title }
            .map { (name, list) -> name to Money.sum(list.map { it.yourMinor }) }
            .sortedByDescending { it.second }
            .take(count)

    /** Open loans, split by which way they run: owed to you, then owed by you. */
    fun owed(states: List<LoanState>, currency: String, zone: ZoneId): Pair<List<Owed>, List<Owed>> {
        val open = states.filter { it.isOpen && it.loan.currency == currency }
        fun rows(lent: Boolean) = open.filter { (it.loan.direction == LoanDirection.LENT) == lent }
            .groupBy { it.loan.personId }
            .map { (person, loans) ->
                Owed(
                    personId = person,
                    amountMinor = Money.sum(loans.map { it.remainingMinor }),
                    dueOn = loans.mapNotNull { it.loan.dueOn }.minOrNull(),
                    since = loans.mapNotNull { it.lentAt }.minOrNull()?.atZone(zone)?.toLocalDate()
                )
            }
            .sortedByDescending { it.amountMinor }
        return rows(lent = true) to rows(lent = false)
    }

    /**
     * The lowest balance with [amountMinor] spent on [on] (each month for a year when [monthly]),
     * looked at a month past the last payment.
     */
    fun afford(inputs: ForecastInputs, amountMinor: Long, on: LocalDate, monthly: Boolean): Affordability {
        val day = if (on.isBefore(inputs.today)) inputs.today else on
        val payments = (0 until if (monthly) MONTHS_A_YEAR else 1).map { Scheduled(day.plusMonths(it.toLong()), "", amountMinor) }
        val horizon = maxOf(payments.last().date, inputs.cycle.end).plusMonths(1)
        val lowest = Forecasts.path(inputs, horizon, payments)
            .filter { !it.first.isBefore(day) }
            .minWith(compareBy({ it.second }, { it.first }))
        return Affordability(lowest.second, lowest.first)
    }

    private const val MONTHS_A_YEAR = 12
}
