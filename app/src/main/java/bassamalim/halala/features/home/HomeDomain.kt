package bassamalim.halala.features.home

import bassamalim.halala.core.Globals
import bassamalim.halala.core.data.dataSources.room.relations.AccountWithBalance
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.AlertsRepository
import bassamalim.halala.core.domain.Anomaly
import bassamalim.halala.core.data.repositories.BudgetsRepository
import bassamalim.halala.core.data.repositories.CycleOverview
import bassamalim.halala.core.data.repositories.ForecastRepository
import bassamalim.halala.core.domain.ForecastInputs
import bassamalim.halala.core.data.repositories.LoansRepository
import bassamalim.halala.core.data.repositories.RecurringRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.domain.LoanState
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.SeriesState
import bassamalim.halala.core.enums.AccountType
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class HomeDomain @Inject constructor(
    private val accountsRepository: AccountsRepository,
    private val transactionsRepository: TransactionsRepository,
    private val loansRepository: LoansRepository,
    private val recurringRepository: RecurringRepository,
    private val budgetsRepository: BudgetsRepository,
    private val forecastRepository: ForecastRepository,
    private val alertsRepository: AlertsRepository,
    private val clock: Clock
) {

    fun observeAlerts(): Flow<List<Anomaly>> = alertsRepository.observeAlerts()

    fun observeForecast(): Flow<ForecastInputs> = forecastRepository.observeInputs(Globals.PRIMARY_CURRENCY)

    fun observeOverview(): Flow<CycleOverview> = budgetsRepository.observeOverview(Globals.PRIMARY_CURRENCY)

    fun observeLoans(): Flow<List<LoanState>> = loansRepository.observeStates()

    fun observeRecurring(): Flow<List<SeriesState>> = recurringRepository.observeStates()

    fun observeAccounts(): Flow<List<AccountWithBalance>> = accountsRepository.observeAll()

    fun observeTransactions(): Flow<List<TransactionDetail>> = transactionsRepository.observeAll()

    fun zone(): ZoneId = clock.zone

    fun today(): LocalDate = LocalDate.now(clock)

    companion object {

        /** The board's "Coming up": the next few due within a month. */
        const val COMING_UP_COUNT = 3

        /** What sits in bank accounts, in SAR: active accounts other than the wallet. */
        fun bankTotal(accounts: List<AccountWithBalance>): Long = Money.sum(
            bankAccounts(accounts).map { it.balanceMinor }
        )

        fun bankAccounts(accounts: List<AccountWithBalance>): List<AccountWithBalance> =
            accounts.filter {
                !it.account.archived && it.account.type != AccountType.CASH && it.account.type.listed &&
                        it.account.currency == Globals.PRIMARY_CURRENCY
            }

        /** How many recent transactions Home lists before "See all". */
        const val RECENT_COUNT = 5
    }
}
