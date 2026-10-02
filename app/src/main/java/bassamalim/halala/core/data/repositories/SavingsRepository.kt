package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.room.daos.AccountsDao
import bassamalim.halala.core.data.dataSources.room.daos.SavingsDao
import bassamalim.halala.core.data.dataSources.room.daos.TransactionsDao
import bassamalim.halala.core.data.dataSources.room.entities.SavingsTerms
import bassamalim.halala.core.data.dataSources.room.relations.AccountWithBalance
import bassamalim.halala.core.domain.Savings
import bassamalim.halala.core.domain.Term
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.enums.Direction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** A savings account with its terms (if you gave them) and where it stands. */
data class SavingsAccount(
    val account: AccountWithBalance,
    val terms: SavingsTerms?,
    /** For a term deposit, its current term. */
    val term: Term?,
    /** The month's lowest balance (Hasad's profit base). */
    val lowestThisMonthMinor: Long
)

/** Savings accounts and their terms: Awaeed term deposits and Hasad monthly-profit savings. */
@Singleton
class SavingsRepository @Inject constructor(
    private val savingsDao: SavingsDao,
    private val accountsDao: AccountsDao,
    private val transactionsDao: TransactionsDao,
    private val clock: Clock
) {

    fun observe(): Flow<List<SavingsAccount>> = combine(
        accountsDao.observeAllWithBalance(),
        savingsDao.observeAll(),
        transactionsDao.observeAllDetails()
    ) { accounts, terms, details ->
        val today = LocalDate.now(clock)
        val byAccount = terms.associateBy { it.accountId }
        accounts.filter { !it.account.archived && it.account.type == AccountType.SAVINGS }.map { account ->
            val flows = details.filter { it.transaction.accountId == account.account.id }
                .groupBy { it.transaction.occurredAt.atZone(clock.zone).toLocalDate() }
                .mapValues { (_, list) -> list.sumOf { if (it.transaction.direction == Direction.CREDIT) it.transaction.amountMinor else -it.transaction.amountMinor } }
            val accountTerms = byAccount[account.account.id]
            SavingsAccount(
                account = account,
                terms = accountTerms,
                term = accountTerms?.let { Savings.termOf(it, account.balanceMinor, today) },
                lowestThisMonthMinor = Savings.lowestThisMonth(account.balanceMinor, flows, today)
            )
        }
    }

    suspend fun getAll(): List<SavingsTerms> = savingsDao.getAll()

    suspend fun get(accountId: Long): SavingsTerms? = savingsDao.get(accountId)

    suspend fun put(terms: SavingsTerms) = savingsDao.put(terms)

    suspend fun delete(accountId: Long) = savingsDao.delete(accountId)
}
