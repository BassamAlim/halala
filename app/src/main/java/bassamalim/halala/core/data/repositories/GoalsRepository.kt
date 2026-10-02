package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.room.daos.AccountsDao
import bassamalim.halala.core.data.dataSources.room.daos.GoalsDao
import bassamalim.halala.core.data.dataSources.room.daos.SavingsDao
import bassamalim.halala.core.data.dataSources.room.daos.TransactionsDao
import bassamalim.halala.core.data.dataSources.room.entities.SavingsGoal
import bassamalim.halala.core.domain.GoalState
import bassamalim.halala.core.domain.Goals
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.enums.Direction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    private val clock: Clock
) {

    fun observeStates(): Flow<List<GoalState>> = combine(
        goalsDao.observeAll(),
        accountsDao.observeAllWithBalance(),
        transactionsDao.observeAllDetails(),
        savingsDao.observeDeposits()
    ) { goals, accounts, details, deposits ->
        val today = LocalDate.now(clock)
        val since = today.minusMonths(3).atStartOfDay(clock.zone).toInstant()
        val balances = accounts.associate { it.account.id to it.balanceMinor }
        val arrived = details.associate { it.transaction.id to it.transaction }
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
                Money.sum(ids.map { balances[it] ?: 0 } + held.map { it.amountMinor }),
                flowIn.sum() + held.filter { !it.occurredAt.isBefore(since) }.sumOf { it.amountMinor },
                today
            )
        }
    }

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
}
