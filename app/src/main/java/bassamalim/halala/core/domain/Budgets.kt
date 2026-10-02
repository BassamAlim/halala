package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.Budget
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.enums.BudgetScope
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import java.time.LocalDate
import java.time.ZoneId

/** Where one budget stands this cycle. Amounts in the budget's currency, minor units. */
data class BudgetStatus(
    val budget: Budget,
    /** The budget, plus what rolled over from last cycle. */
    val limitMinor: Long,
    val spentMinor: Long,
    val state: BudgetState,
    /** Spent further through the budget than through the cycle, by more than the margin. */
    val paceAhead: Boolean
) {
    val leftMinor get() = limitMinor - spentMinor
}

/**
 * Budgets against the pay cycle: what counts as spending, what each scope takes, rollover and
 * pace. Spending is money out that counts in totals, your share of it ([TransactionDetail.yourMinor]).
 */
object Budgets {

    /** Spending further through the budget than the cycle by this much is "spending fast". */
    const val PACE_MARGIN = 0.10

    fun isSpending(detail: TransactionDetail) =
        detail.transaction.direction == Direction.DEBIT && detail.transaction.kind.countsInTotals && !detail.isInternalTransfer

    fun matches(budget: Budget, detail: TransactionDetail): Boolean = when (budget.scope) {
        BudgetScope.TOTAL -> true
        BudgetScope.CATEGORY -> detail.transaction.categoryId == budget.categoryId
        BudgetScope.EXPENSE_TYPE -> detail.transaction.expenseType == budget.expenseType
        BudgetScope.MERCHANT -> detail.merchantId == budget.merchantId
    }

    /** What [budget]'s scope spent in [cycle]. */
    fun spent(budget: Budget, details: List<TransactionDetail>, cycle: PayCycle, zone: ZoneId): Long = Money.sum(
        details
            .filter { isSpending(it) && it.transaction.currency == budget.currency && matches(budget, it) }
            .filter { cycle.contains(it.transaction.occurredAt.atZone(zone).toLocalDate()) }
            .map { it.yourMinor }
    )

    fun statusOf(
        budget: Budget,
        details: List<TransactionDetail>,
        cycle: PayCycle,
        previous: PayCycle?,
        today: LocalDate,
        zone: ZoneId
    ): BudgetStatus {
        val rolled = if (budget.rollover && previous != null)
            maxOf(0, budget.amountMinor - spent(budget, details, previous, zone)) else 0
        val limit = Math.addExact(budget.amountMinor, rolled)
        val spent = spent(budget, details, cycle, zone)
        val share = if (limit == 0L) 0.0 else spent.toDouble() / limit
        return BudgetStatus(
            budget = budget,
            limitMinor = limit,
            spentMinor = spent,
            state = BudgetState.of(spent, limit),
            paceAhead = spent <= limit && share > cycle.elapsed(today) + PACE_MARGIN
        )
    }

    /** Salary credits, for the pay cycle. */
    fun salaries(details: List<TransactionDetail>, zone: ZoneId): List<SalaryCredit> = details
        .filter { it.transaction.kind == TransactionKind.SALARY && it.transaction.direction == Direction.CREDIT }
        .map { SalaryCredit(it.transaction.occurredAt.atZone(zone).toLocalDate(), it.transaction.amountMinor) }
}
