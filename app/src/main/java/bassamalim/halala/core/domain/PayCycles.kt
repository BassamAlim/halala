package bassamalim.halala.core.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A salary as it arrived: the day and how much. */
data class SalaryCredit(val date: LocalDate, val amountMinor: Long)

/**
 * A pay cycle: from a salary to the next. [end] is the day the next salary is expected (the
 * cycle runs up to, not including, it); while a salary is late the cycle runs on past it, to
 * tomorrow. Without any salary the cycle is the calendar month ([fromSalary] false).
 */
data class PayCycle(
    val start: LocalDate,
    val end: LocalDate,
    val fromSalary: Boolean,
    /** What a salary usually is: the median of the last three. Null without one. */
    val salaryMinor: Long?
) {
    val days get() = ChronoUnit.DAYS.between(start, end).coerceAtLeast(1)

    fun contains(date: LocalDate) = !date.isBefore(start) && date.isBefore(end)

    fun daysLeft(today: LocalDate) = ChronoUnit.DAYS.between(today, end).coerceAtLeast(0)

    /** How far through it [today] is, 0 to 1. */
    fun elapsed(today: LocalDate): Double =
        (ChronoUnit.DAYS.between(start, today).toDouble() / days).coerceIn(0.0, 1.0)
}

/**
 * Pay cycles from salary credits (the spec's: salary to salary, and a salary can come a few days
 * early or late). A credit far smaller than the usual salary (a bonus, an allowance) doesn't
 * start a cycle; neither does a second one within [MIN_GAP_DAYS] of the last.
 */
object PayCycles {

    /** Credits closer than this to the last are the same pay day (an allowance after the salary). */
    const val MIN_GAP_DAYS = 20L

    /** A salary older than this no longer sets the cycle: it falls back to the month. */
    const val STALE_DAYS = 45L

    /** The pay days: credits at least half the usual salary, at least [MIN_GAP_DAYS] apart, oldest first. */
    fun payDays(credits: List<SalaryCredit>): List<SalaryCredit> {
        if (credits.isEmpty()) return emptyList()
        val usual = median(credits.sortedBy { it.date }.takeLast(3).map { it.amountMinor })
        val big = credits.filter { it.amountMinor * 2 >= usual }.sortedBy { it.date }
        val days = mutableListOf<SalaryCredit>()
        for (credit in big) {
            val last = days.lastOrNull()
            if (last == null || ChronoUnit.DAYS.between(last.date, credit.date) >= MIN_GAP_DAYS) days += credit
        }
        return days
    }

    /** The cycle [today] is in. */
    fun current(credits: List<SalaryCredit>, today: LocalDate): PayCycle {
        val days = payDays(credits.filter { !it.date.isAfter(today) })
        val last = days.lastOrNull()
        val salary = days.takeLast(3).takeIf { it.isNotEmpty() }?.let { recent -> median(recent.map { it.amountMinor }) }
        if (last == null || ChronoUnit.DAYS.between(last.date, today) > STALE_DAYS) {
            val start = today.withDayOfMonth(1)
            return PayCycle(start, start.plusMonths(1), fromSalary = false, salaryMinor = salary)
        }
        val expected = last.date.plusMonths(1)
        return PayCycle(last.date, if (expected.isAfter(today)) expected else today.plusDays(1), fromSalary = true, salaryMinor = salary)
    }

    /**
     * The [count] cycles before [current], newest first: between consecutive pay days, or
     * calendar months for a cycle that isn't from salary.
     */
    fun previous(credits: List<SalaryCredit>, current: PayCycle, count: Int): List<PayCycle> {
        if (!current.fromSalary) return (1..count).map {
            val start = current.start.minusMonths(it.toLong())
            PayCycle(start, start.plusMonths(1), fromSalary = false, salaryMinor = current.salaryMinor)
        }
        val days = payDays(credits.filter { it.date.isBefore(current.start) }).map { it.date } + current.start
        return days.zipWithNext { a, b -> PayCycle(a, b, fromSalary = true, salaryMinor = current.salaryMinor) }
            .reversed()
            .take(count)
    }

    /** The middle value (the lower of the two middles for an even count). */
    fun median(values: List<Long>): Long {
        require(values.isNotEmpty())
        val sorted = values.sorted()
        return sorted[(sorted.size - 1) / 2]
    }
}
