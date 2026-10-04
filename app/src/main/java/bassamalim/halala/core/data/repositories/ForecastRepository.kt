package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.room.daos.AccountsDao
import bassamalim.halala.core.data.dataSources.room.daos.TransactionsDao
import bassamalim.halala.core.domain.Budgets
import bassamalim.halala.core.domain.Charges
import bassamalim.halala.core.domain.ForecastInputs
import bassamalim.halala.core.domain.Forecasts
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.PayCycles
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.enums.SeriesStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the forecast starts from, read from the ledger as it is: the money you can spend now, the
 * pay cycle, how fast recent cycles spent outside subscriptions and bills, and what those are due
 * to take.
 */
@Singleton
class ForecastRepository @Inject constructor(
    private val accountsDao: AccountsDao,
    private val transactionsDao: TransactionsDao,
    private val recurringRepository: RecurringRepository,
    private val clock: Clock
) {

    fun observeInputs(currency: String): Flow<ForecastInputs> = combine(
        accountsDao.observeAllWithBalance(),
        transactionsDao.observeAllDetails(),
        recurringRepository.observeStates()
    ) { accounts, details, states ->
        val today = LocalDate.now(clock)
        val zone = clock.zone
        val salaries = Budgets.salaries(details, zone)
        val cycle = PayCycles.current(salaries, today)
        val active = states.filter { it.series.status == SeriesStatus.ACTIVE && it.series.currency == currency }
        val charged = active.flatMap { Charges.of(it.series, details, zone) }.map { it.transactionId }.toSet()

        fun variable(from: LocalDate, until: LocalDate) = Money.sum(
            details.filter {
                Budgets.isSpending(it) && it.transaction.currency == currency && it.transaction.id !in charged &&
                        it.transaction.occurredAt.atZone(zone).toLocalDate().let { day -> !day.isBefore(from) && day.isBefore(until) }
            }.map { it.yourMinor }
        )

        val previous = PayCycles.previous(salaries, cycle, RECENT_CYCLES)
        val rates = previous.map { Forecasts.rate(variable(it.start, it.end), it.days) }
            .ifEmpty {
                // No finished cycle yet: this one so far, once a week of it has passed.
                val elapsed = Forecasts.daysBetween(cycle.start, today)
                if (elapsed >= MIN_DAYS_SO_FAR) listOf(Forecasts.rate(variable(cycle.start, today.plusDays(1)), elapsed)) else emptyList()
            }

        ForecastInputs(
            today = today,
            balanceMinor = Money.sum(
                accounts.filter { !it.account.archived && it.account.currency == currency && it.account.type in SPENDABLE }
                    .map { it.balanceMinor }
            ),
            cycle = cycle,
            variableRates = rates,
            scheduled = active.flatMap { Forecasts.occurrences(it, today, today.plusMonths(HORIZON_MONTHS)) },
            salaryMinor = cycle.salaryMinor
        )
    }.flowOn(Dispatchers.Default)

    private companion object {
        const val RECENT_CYCLES = 3
        const val MIN_DAYS_SO_FAR = 7L
        const val HORIZON_MONTHS = 13L

        /** Where day-to-day money is: not savings or investments, which a forecast leaves alone. */
        val SPENDABLE = setOf(AccountType.CURRENT, AccountType.CARD, AccountType.WALLET, AccountType.CASH)
    }
}
