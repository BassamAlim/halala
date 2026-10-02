package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.SavingsGoal
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
}
