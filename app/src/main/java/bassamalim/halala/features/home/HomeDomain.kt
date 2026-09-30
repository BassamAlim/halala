package bassamalim.halala.features.home

import bassamalim.halala.core.Globals
import bassamalim.halala.core.data.dataSources.room.relations.AccountWithBalance
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.enums.AccountType
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class HomeDomain @Inject constructor(
    private val accountsRepository: AccountsRepository,
    private val transactionsRepository: TransactionsRepository,
    private val clock: Clock
) {

    fun observeAccounts(): Flow<List<AccountWithBalance>> = accountsRepository.observeAll()

    fun observeTransactions(): Flow<List<TransactionDetail>> = transactionsRepository.observeAll()

    fun zone(): ZoneId = clock.zone

    fun today(): LocalDate = LocalDate.now(clock)

    companion object {

        /** What sits in bank accounts, in SAR: active accounts other than the wallet. */
        fun bankTotal(accounts: List<AccountWithBalance>): Long = Money.sum(
            bankAccounts(accounts).map { it.balanceMinor }
        )

        fun bankAccounts(accounts: List<AccountWithBalance>): List<AccountWithBalance> =
            accounts.filter {
                !it.account.archived && it.account.type != AccountType.CASH &&
                        it.account.currency == Globals.PRIMARY_CURRENCY
            }

        /** How many recent transactions Home lists before "See all". */
        const val RECENT_COUNT = 5
    }
}
