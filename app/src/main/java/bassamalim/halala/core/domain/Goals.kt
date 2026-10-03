package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.GoalContribution
import bassamalim.halala.core.data.dataSources.room.entities.SavingsGoal
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Where a goal stands: saved so far, what each month needs to reach it in time, what you have been saving. */
data class GoalState(
    val goal: SavingsGoal,
    val savedMinor: Long,
    /** A month's saving to reach it by its date; null without a date, zero once reached. */
    val neededMonthlyMinor: Long?,
    /** The average a month went into its accounts over the last three. */
    val averageMonthlyMinor: Long
) {
    val reached get() = savedMinor >= goal.targetMinor
}

object Goals {

    /** Months from this one to the target's, at least one (the target month counts). */
    fun monthsLeft(today: LocalDate, target: LocalDate): Long =
        ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(target)).coerceAtLeast(0) + 1

    /** [left] over [months], rounded up: a goal is reached by saving at least this. */
    fun perMonth(left: Long, months: Long): Long = if (left <= 0) 0 else (left + months - 1) / months

    fun stateOf(goal: SavingsGoal, savedMinor: Long, savedLastThreeMonthsMinor: Long, today: LocalDate) = GoalState(
        goal = goal,
        savedMinor = savedMinor,
        neededMonthlyMinor = goal.targetDate?.let { perMonth(goal.targetMinor - savedMinor, monthsLeft(today, it)) },
        averageMonthlyMinor = savedLastThreeMonthsMinor / 3
    )

    /** The accounts money is spent from: what is moved out of them toward a goal is saved. */
    val SPENDABLE = setOf(AccountType.CURRENT, AccountType.CARD, AccountType.WALLET, AccountType.CASH)

    /**
     * The kinds that can be marked toward a goal: money sent or received (to a broker, say), a
     * move between your own accounts, and what is already saving or investing. Never spending at
     * a shop, a salary or a loan.
     */
    val MARKABLE = setOf(
        TransactionKind.TRANSFER_OUT, TransactionKind.TRANSFER_IN, TransactionKind.INTERNAL_TRANSFER,
        TransactionKind.SAVINGS_DEPOSIT, TransactionKind.SAVINGS_WITHDRAWAL,
        TransactionKind.INVESTMENT_BUY, TransactionKind.INVESTMENT_SELL, TransactionKind.OTHER
    )

    /** Whether a transaction can be marked toward a goal: one of [MARKABLE], not a loan's or a split purchase. */
    fun canContribute(detail: TransactionDetail, isLoanPart: Boolean): Boolean =
        !isLoanPart && detail.sharedMinor == 0L && detail.transaction.kind in MARKABLE

    /**
     * Whether marking it toward a goal would most likely be taking money out of the goal: money
     * coming in from outside, or a move from an account you don't spend from into one you do.
     */
    fun withdrawnByDefault(detail: TransactionDetail, types: Map<Long, AccountType>): Boolean {
        val tx = detail.transaction
        val other = detail.counterpartAccountId ?: return tx.direction == Direction.CREDIT
        val (from, to) = if (tx.direction == Direction.DEBIT) tx.accountId to other else other to tx.accountId
        return types[from] !in SPENDABLE && types[to] in SPENDABLE
    }

    /**
     * The kind a plain transaction takes once it is toward a goal: saving isn't spending, nor
     * taking it back income. Null for a move between your own accounts, which counts as neither
     * already and keeps its kind.
     */
    fun kindToward(detail: TransactionDetail, withdrawn: Boolean): TransactionKind? = when {
        detail.isInternalTransfer -> null
        withdrawn -> TransactionKind.SAVINGS_WITHDRAWAL
        else -> TransactionKind.SAVINGS_DEPOSIT
    }

    /**
     * What [contributions] (each with its transaction) put toward [goal] from [since] on (all of
     * them when null): in the goal's currency, less what was taken out, and none that an account
     * the goal is saved in already counts (it would be counted twice).
     */
    fun contributed(
        goal: SavingsGoal,
        contributions: List<Pair<GoalContribution, TransactionDetail>>,
        since: Instant? = null
    ): Long = Money.sum(
        contributions
            .filter { (c, d) ->
                c.goalId == goal.id && d.transaction.currency == goal.currency &&
                        d.transaction.accountId !in goal.accountIds && d.counterpartAccountId !in goal.accountIds &&
                        (since == null || !d.transaction.occurredAt.isBefore(since))
            }
            .map { (c, d) -> if (c.withdrawn) -d.transaction.amountMinor else d.transaction.amountMinor }
    )
}
