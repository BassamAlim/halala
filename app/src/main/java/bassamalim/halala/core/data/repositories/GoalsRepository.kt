package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.room.daos.AccountsDao
import bassamalim.halala.core.data.dataSources.room.daos.GoalsDao
import bassamalim.halala.core.data.dataSources.room.daos.LoansDao
import bassamalim.halala.core.data.dataSources.room.daos.SavingsDao
import bassamalim.halala.core.data.dataSources.room.daos.TransactionsDao
import bassamalim.halala.core.data.dataSources.room.entities.GoalContribution
import bassamalim.halala.core.data.dataSources.room.entities.SavingsGoal
import bassamalim.halala.core.domain.GoalState
import bassamalim.halala.core.domain.Goals
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.enums.Direction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Savings goals, each measured by the balances of the accounts it is saved in and the deposits made for it. */
@Singleton
class GoalsRepository @Inject constructor(
    private val goalsDao: GoalsDao,
    private val accountsDao: AccountsDao,
    private val transactionsDao: TransactionsDao,
    private val savingsDao: SavingsDao,
    private val loansDao: LoansDao,
    private val clock: Clock
) {

    fun observeStates(): Flow<List<GoalState>> = combine(
        goalsDao.observeAll(),
        accountsDao.observeAllWithBalance(),
        transactionsDao.observeAllDetails(),
        savingsDao.observeDeposits(),
        goalsDao.observeContributions()
    ) { goals, accounts, details, deposits, contributions ->
        val today = LocalDate.now(clock)
        val since = today.minusMonths(3).atStartOfDay(clock.zone).toInstant()
        val balances = accounts.associate { it.account.id to it.balanceMinor }
        val arrived = details.associate { it.transaction.id to it.transaction }
        val byId = details.associateBy { it.transaction.id }
        val toward = contributions.mapNotNull { c -> byId[c.transactionId]?.let { c to it } }
        goals.map { goal ->
            val ids = goal.accountIds.toSet()
            val flowIn = details
                .filter { it.transaction.accountId in ids && !it.transaction.occurredAt.isBefore(since) }
                .map { if (it.transaction.direction == Direction.CREDIT) it.transaction.amountMinor else -it.transaction.amountMinor }
            // Deposits made for it, while they run, in the goal's currency.
            val held = deposits.filter { it.goalId == goal.id && it.closedOn == null }
                .mapNotNull { arrived[it.transactionId] }.filter { it.currency == goal.currency }
            Goals.stateOf(
                goal,
                Money.sum(ids.map { balances[it] ?: 0 } + held.map { it.amountMinor } + Goals.contributed(goal, toward)),
                flowIn.sum() + held.filter { !it.occurredAt.isBefore(since) }.sumOf { it.amountMinor } +
                        Goals.contributed(goal, toward, since),
                today
            )
        }
    }

    fun observeAll(): Flow<List<SavingsGoal>> = goalsDao.observeAll()

    suspend fun getAll(): List<SavingsGoal> = goalsDao.getAll()

    suspend fun get(id: Long): SavingsGoal? = goalsDao.get(id)

    suspend fun save(goal: SavingsGoal): Long {
        require(goal.targetMinor > 0) { "A goal is positive." }
        return if (goal.id == 0L) goalsDao.insert(
            goal.copy(uid = goal.uid.ifEmpty { UUID.randomUUID().toString() }, createdAt = clock.instant())
        ) else {
            goalsDao.update(goal)
            goal.id
        }
    }

    suspend fun delete(id: Long) = goalsDao.delete(id)

    fun observeContributions(): Flow<List<GoalContribution>> = goalsDao.observeContributions()

    suspend fun getContributions(): List<GoalContribution> = goalsDao.getContributions()

    /**
     * [transactionId] went toward [goalId] (or, [withdrawn], came out of it). A plain one stops
     * counting as spending or income. False when it can't be (a loan's, a split purchase, or
     * already toward a goal).
     */
    suspend fun contribute(transactionId: Long, goalId: Long, withdrawn: Boolean): Boolean {
        val detail = transactionsDao.observeDetail(transactionId).first() ?: return false
        if (goalsDao.get(goalId) == null || goalsDao.getContributionFor(transactionId) != null) return false
        val loanPart = loansDao.getEventFor(transactionId) != null
        if (!Goals.canContribute(detail, loanPart)) return false
        // A move is marked by its sending leg, so both legs read as the same contribution.
        val id = if (detail.isInternalTransfer && detail.isTransferInLeg) detail.counterpartId!! else transactionId
        if (id != transactionId && goalsDao.getContributionFor(id) != null) return false
        val kind = Goals.kindToward(detail, withdrawn)
        goalsDao.contribute(
            GoalContribution(
                uid = UUID.randomUUID().toString(),
                goalId = goalId,
                transactionId = id,
                withdrawn = withdrawn,
                kindBefore = kind?.let { detail.transaction.kind }
            ),
            kind
        )
        return true
    }

    /** The contribution [transactionId] (either leg of a move) is part of, if any. */
    suspend fun contributionFor(transactionId: Long): GoalContribution? {
        goalsDao.getContributionFor(transactionId)?.let { return it }
        val pair = transactionsDao.getTransferFor(transactionId) ?: return null
        return goalsDao.getContributionFor(if (pair.outTransactionId == transactionId) pair.inTransactionId else pair.outTransactionId)
    }

    /** No longer toward a goal: it counts as it did before it was marked. */
    suspend fun uncontribute(transactionId: Long) {
        contributionFor(transactionId)?.let { goalsDao.withdrawContribution(it) }
    }
}
