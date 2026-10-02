package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.room.daos.BudgetsDao
import bassamalim.halala.core.data.dataSources.room.daos.TransactionsDao
import bassamalim.halala.core.data.dataSources.room.entities.Budget
import bassamalim.halala.core.domain.BudgetStatus
import bassamalim.halala.core.domain.Budgets
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.PayCycle
import bassamalim.halala.core.domain.PayCycles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Budgets: limits on spending each pay cycle. What was spent is read from the ledger, never stored. */
/** The pay cycle today is in, every budget's status in it, and all spending in it (in [currency]). */
data class CycleOverview(
    val cycle: PayCycle,
    val previous: PayCycle?,
    val statuses: List<BudgetStatus>,
    val spentMinor: Long,
    val currency: String
)

@Singleton
class BudgetsRepository @Inject constructor(
    private val budgetsDao: BudgetsDao,
    private val transactionsDao: TransactionsDao,
    private val clock: Clock
) {

    /** This cycle as budgets see it, kept up to date as money moves. */
    fun observeOverview(currency: String): Flow<CycleOverview> =
        combine(budgetsDao.observeAll(), transactionsDao.observeAllDetails()) { budgets, details ->
            val today = LocalDate.now(clock)
            val salaries = Budgets.salaries(details, clock.zone)
            val cycle = PayCycles.current(salaries, today)
            val previous = PayCycles.previous(salaries, cycle, 1).firstOrNull()
            CycleOverview(
                cycle = cycle,
                previous = previous,
                statuses = budgets.map { Budgets.statusOf(it, details, cycle, previous, today, clock.zone) },
                spentMinor = Money.sum(
                    details.filter {
                        Budgets.isSpending(it) && it.transaction.currency == currency &&
                                cycle.contains(it.transaction.occurredAt.atZone(clock.zone).toLocalDate())
                    }.map { it.yourMinor }
                ),
                currency = currency
            )
        }

    fun observeAll(): Flow<List<Budget>> = budgetsDao.observeAll()

    suspend fun getAll(): List<Budget> = budgetsDao.getAll()

    suspend fun get(id: Long): Budget? = budgetsDao.get(id)

    /** Adds [budget] (id 0) or saves changes to it. */
    suspend fun save(budget: Budget): Long {
        require(budget.amountMinor > 0) { "Budgets are positive." }
        return if (budget.id == 0L) budgetsDao.insert(
            budget.copy(uid = budget.uid.ifEmpty { UUID.randomUUID().toString() }, createdAt = clock.instant())
        ) else {
            budgetsDao.update(budget)
            budget.id
        }
    }

    suspend fun delete(id: Long) = budgetsDao.delete(id)
}
