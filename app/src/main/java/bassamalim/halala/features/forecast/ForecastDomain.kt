package bassamalim.halala.features.forecast

import bassamalim.halala.core.Globals
import bassamalim.halala.core.data.dataSources.room.relations.AccountWithBalance
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.BudgetsRepository
import bassamalim.halala.core.data.repositories.CycleOverview
import bassamalim.halala.core.data.repositories.ForecastRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.domain.ForecastInputs
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.enums.Direction
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class ForecastDomain @Inject constructor(
    private val forecastRepository: ForecastRepository,
    private val accountsRepository: AccountsRepository,
    private val transactionsRepository: TransactionsRepository,
    private val budgetsRepository: BudgetsRepository,
    private val clock: Clock
) {

    fun observeInputs(): Flow<ForecastInputs> = forecastRepository.observeInputs(Globals.PRIMARY_CURRENCY)

    fun observeAccounts(): Flow<List<AccountWithBalance>> = accountsRepository.observeAll()

    fun observeTransactions(): Flow<List<TransactionDetail>> = transactionsRepository.observeAll()

    fun observeOverview(): Flow<CycleOverview> = budgetsRepository.observeOverview(Globals.PRIMARY_CURRENCY)

    fun today(): LocalDate = LocalDate.now(clock)

    fun zone(): ZoneId = clock.zone

    companion object {

        /**
         * The spendable balance at the end of each day from [from] to [today], worked back from
         * [balanceMinor] now by undoing each day's money in and out of those accounts.
         */
        fun history(
            balanceMinor: Long,
            from: LocalDate,
            today: LocalDate,
            accounts: List<AccountWithBalance>,
            details: List<TransactionDetail>,
            zone: ZoneId
        ): List<Long> {
            val spendable = accounts
                .filter { it.account.type in setOf(AccountType.CURRENT, AccountType.CARD, AccountType.WALLET, AccountType.CASH) }
                .filter { it.account.currency == Globals.PRIMARY_CURRENCY }
                .map { it.account.id }.toSet()
            val netByDay = details
                .filter { it.transaction.accountId in spendable }
                .groupBy { it.transaction.occurredAt.atZone(zone).toLocalDate() }
                .mapValues { (_, list) ->
                    list.sumOf { if (it.transaction.direction == Direction.CREDIT) it.transaction.amountMinor else -it.transaction.amountMinor }
                }
            val days = generateSequence(today) { it.minusDays(1) }.takeWhile { !it.isBefore(from) }.toList()
            var balance = balanceMinor
            val backwards = mutableListOf<Long>()
            for (day in days) {
                backwards += balance
                balance -= netByDay[day] ?: 0
            }
            return backwards.reversed()
        }
    }
}
