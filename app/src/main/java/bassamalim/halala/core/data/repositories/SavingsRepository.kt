package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.room.daos.AccountsDao
import bassamalim.halala.core.data.dataSources.room.daos.GoalsDao
import bassamalim.halala.core.data.dataSources.room.daos.SavingsDao
import bassamalim.halala.core.data.dataSources.room.daos.TransactionsDao
import bassamalim.halala.core.data.dataSources.room.entities.Deposit
import bassamalim.halala.core.data.dataSources.room.entities.SavingsTerms
import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.data.dataSources.room.relations.AccountWithBalance
import bassamalim.halala.core.domain.Savings
import bassamalim.halala.core.domain.Term
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.models.TransferDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
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

/** One term deposit still running: what went in and when (its transfer's), its term and what it is for. */
data class DepositState(
    val deposit: Deposit,
    val amountMinor: Long,
    val currency: String,
    val start: LocalDate,
    val term: Term?,
    val goalName: String?
)

/** Savings accounts and their terms (Hasad, or an Awaeed you keep as an account), and term deposits each on their own. */
@Singleton
class SavingsRepository @Inject constructor(
    private val savingsDao: SavingsDao,
    private val accountsDao: AccountsDao,
    private val transactionsDao: TransactionsDao,
    private val goalsDao: GoalsDao,
    private val transactions: TransactionsRepository,
    private val clock: Clock
) {

    /** The deposits not yet paid out, oldest first. */
    fun observeDeposits(): Flow<List<DepositState>> = combine(
        savingsDao.observeDeposits(),
        transactionsDao.observeAllDetails(),
        goalsDao.observeAll()
    ) { deposits, details, goals ->
        val today = LocalDate.now(clock)
        val arrived = details.associate { it.transaction.id to it.transaction }
        val names = goals.associate { it.id to it.name }
        deposits.filter { it.closedOn == null }.mapNotNull { deposit ->
            val leg = arrived[deposit.transactionId] ?: return@mapNotNull null
            val start = leg.occurredAt.atZone(clock.zone).toLocalDate()
            DepositState(
                deposit = deposit,
                amountMinor = leg.amountMinor,
                currency = leg.currency,
                start = start,
                term = Savings.termOf(start, deposit.tenorMonths, deposit.ratePercent, deposit.maturityChoice, leg.amountMinor, today),
                goalName = names[deposit.goalId]
            )
        }
    }

    suspend fun getDeposits(): List<Deposit> = savingsDao.getDeposits()

    suspend fun getDeposit(id: Long): Deposit? = savingsDao.getDeposit(id)

    /** A deposit for the leg an SMS put in the bank's holding account. */
    suspend fun addDeposit(transactionId: Long): Long =
        savingsDao.insertDeposit(Deposit(uid = UUID.randomUUID().toString(), transactionId = transactionId))

    suspend fun saveDeposit(deposit: Deposit) = savingsDao.updateDeposit(deposit)

    /**
     * The bank paid [amountMinor] out of [holdingId] at [at]: closes the running deposit it was
     * and returns what had gone into it (the rest is profit), or null when none fits.
     */
    suspend fun closePaid(holdingId: Long, amountMinor: Long, at: Instant): Long? {
        // ponytail: matched by amount, since the SMS names no deposit: the largest that went in
        // before, at no less than 80% of what came back; of equals, the oldest. Match on the
        // expected maturity too if same-sized deposits ever close out of order.
        val (deposit, arrived) = savingsDao.getDeposits().filter { it.closedOn == null }
            .mapNotNull { held -> transactionsDao.get(held.transactionId)?.let { held to it } }
            .filter { (_, leg) -> leg.accountId == holdingId && leg.occurredAt < at && leg.amountMinor <= amountMinor && leg.amountMinor >= amountMinor / 5 * 4 }
            .maxWithOrNull(compareBy<Pair<Deposit, Transaction>> { it.second.amountMinor }.thenByDescending { it.second.occurredAt })
            ?: return null
        savingsDao.updateDeposit(deposit.copy(closedOn = at.atZone(clock.zone).toLocalDate()))
        return arrived.amountMinor
    }

    /**
     * You say the deposit ended, for a bank whose SMS doesn't ([closePaid] does it when one
     * arrives): what went in moves back, today, to the account it came from, and it is closed.
     */
    suspend fun payOut(id: Long) {
        val deposit = savingsDao.getDeposit(id)?.takeIf { it.closedOn == null } ?: return
        val arrived = transactionsDao.get(deposit.transactionId) ?: return
        val from = transactionsDao.getTransferFor(arrived.id)?.let { transactionsDao.get(it.outTransactionId) }
        if (from != null) transactions.addTransfer(
            TransferDraft(arrived.accountId, from.accountId, arrived.amountMinor, clock.instant(), TransactionKind.SAVINGS_WITHDRAWAL, arrived.title)
        )
        savingsDao.updateDeposit(deposit.copy(closedOn = LocalDate.now(clock)))
    }

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
