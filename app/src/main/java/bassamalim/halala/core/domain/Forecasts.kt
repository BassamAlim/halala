package bassamalim.halala.core.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** A payment expected on a day: a bill, a subscription, a planned transfer. Positive, money out. */
data class Scheduled(val date: LocalDate, val name: String, val amountMinor: Long)

/**
 * What a forecast starts from, all in one currency. [variableRates] is each recent cycle's
 * variable spending per day (everything but what subscriptions and bills charge), in minor units;
 * [scheduled] the payments expected from today on.
 */
data class ForecastInputs(
    val today: LocalDate,
    val balanceMinor: Long,
    val cycle: PayCycle,
    val variableRates: List<Long>,
    val scheduled: List<Scheduled>,
    val salaryMinor: Long?
)

/** An estimate with its band: [low] and [high] from the spending rates of recent cycles. */
data class Estimate(val midMinor: Long, val lowMinor: Long, val highMinor: Long)

/** The month-by-month view: what comes in, what is scheduled out, what is left. */
data class MonthAhead(val month: YearMonth, val leftMinor: Long, val biggest: List<Scheduled>)

/** "Can I afford it?": the lowest balance reached with it, and when. */
data class Affordability(val lowestMinor: Long, val lowestOn: LocalDate) {
    val affordable get() = lowestMinor >= 0
}

/**
 * The spec's forecasts, in exact integers: balance now, plus salary when it comes, less what
 * is scheduled and less variable spending at the median rate of the last cycles (the band from
 * the slowest and the fastest).
 */
object Forecasts {

    /** Variable spending per day over [days], rounded half up. */
    fun rate(spentMinor: Long, days: Long): Long =
        BigDecimal.valueOf(spentMinor).divide(BigDecimal.valueOf(days.coerceAtLeast(1)), 0, RoundingMode.HALF_UP).longValueExact()

    /** The balance at the end of this cycle (the day before the next salary); null with no rate to go on. */
    fun endOfCycle(inputs: ForecastInputs): Estimate? {
        if (inputs.variableRates.isEmpty()) return null
        val days = inputs.cycle.daysLeft(inputs.today)
        val scheduled = Money.sum(inputs.scheduled.filter { it.date.isBefore(inputs.cycle.end) }.map { it.amountMinor })
        fun at(rate: Long) = inputs.balanceMinor - scheduled - Math.multiplyExact(rate, days)
        return Estimate(
            midMinor = at(PayCycles.median(inputs.variableRates)),
            lowMinor = at(inputs.variableRates.max()),
            highMinor = at(inputs.variableRates.min())
        )
    }

    /**
     * The balance at the end of each day from today to [until], at the median rate: scheduled
     * payments and [extra] ones on their days, a salary on each expected pay day.
     */
    fun path(inputs: ForecastInputs, until: LocalDate, extra: List<Scheduled> = emptyList()): List<Pair<LocalDate, Long>> {
        val rate = inputs.variableRates.takeIf { it.isNotEmpty() }?.let(PayCycles::median) ?: 0
        val out = (inputs.scheduled + extra).groupBy { it.date }.mapValues { (_, list) -> Money.sum(list.map { it.amountMinor }) }
        val paydays = if (inputs.salaryMinor != null && inputs.cycle.fromSalary)
            generateSequence(inputs.cycle.end) { it.plusMonths(1) }.takeWhile { !it.isAfter(until) }.toSet()
        else emptySet()

        var balance = inputs.balanceMinor
        val days = mutableListOf<Pair<LocalDate, Long>>()
        var day = inputs.today
        while (!day.isAfter(until)) {
            // Today's spending so far is already in the balance.
            if (day != inputs.today) balance -= rate
            balance -= out[day] ?: 0
            if (day in paydays) balance += inputs.salaryMinor!!
            days += day to balance
            day = day.plusDays(1)
        }
        return days
    }

    /** What the lowest balance would be, and when, with [amountMinor] spent on [on]; looked at a month past it. */
    fun afford(inputs: ForecastInputs, amountMinor: Long, on: LocalDate): Affordability {
        val day = if (on.isBefore(inputs.today)) inputs.today else on
        val horizon = maxOf(day, inputs.cycle.end).plusMonths(1)
        val lowest = path(inputs, horizon, listOf(Scheduled(day, "", amountMinor)))
            .filter { !it.first.isBefore(day) }
            .minWith(compareBy({ it.second }, { it.first }))
        return Affordability(lowest.second, lowest.first)
    }

    /**
     * What each of the next [count] months leaves over: salary, less what is scheduled in it,
     * less a month of variable spending at the median rate. [scheduled] should reach that far.
     */
    fun monthsAhead(inputs: ForecastInputs, count: Int): List<MonthAhead> {
        val rate = inputs.variableRates.takeIf { it.isNotEmpty() }?.let(PayCycles::median) ?: 0
        val first = YearMonth.from(inputs.today).plusMonths(1)
        return (0 until count).map { offset ->
            val month = first.plusMonths(offset.toLong())
            val due = inputs.scheduled.filter { YearMonth.from(it.date) == month }
            val variable = Math.multiplyExact(rate, month.lengthOfMonth().toLong())
            val left = (inputs.salaryMinor ?: 0) - Money.sum(due.map { it.amountMinor }) - variable
            MonthAhead(month, left, due.sortedByDescending { it.amountMinor }.take(2))
        }
    }

    /** The payments [state] expects from [from] up to [until]: its occurrences, while it runs. */
    fun occurrences(state: SeriesState, from: LocalDate, until: LocalDate): List<Scheduled> {
        val next = state.nextDue ?: return emptyList()
        val series = state.series
        val amount = state.raisedTo ?: series.amountMinor
        // Count from the anchor to keep months from drifting: find the occurrence index of [next].
        var k = 0L
        while (Recurring.occurrence(series.anchor, series.every, series.unit, k).isBefore(next)) k++
        val list = mutableListOf<Scheduled>()
        while (true) {
            val date = Recurring.occurrence(series.anchor, series.every, series.unit, k)
            if (date.isAfter(until) || (series.endsOn != null && date.isAfter(series.endsOn))) break
            if (!date.isBefore(from)) list += Scheduled(date, series.name, amount)
            k++
            if (k > MAX_OCCURRENCES) break
        }
        return list
    }

    /** Days between two dates, at least one. */
    fun daysBetween(from: LocalDate, until: LocalDate) = ChronoUnit.DAYS.between(from, until).coerceAtLeast(1)

    private const val MAX_OCCURRENCES = 5_000L
}
