package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.SavingsTerms
import bassamalim.halala.core.enums.MaturityChoice
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A term deposit's current term: when it started and matures, how far through, and the profit expected. */
data class Term(val start: LocalDate, val maturity: LocalDate, val elapsed: Float, val expectedProfitMinor: Long)

/**
 * Awaeed and Hasad (the spec's): a term deposit earns its rate over its term, and a renewing
 * one starts its next term on its maturity day; Hasad pays a month's profit on the month's
 * lowest balance, and nothing under [HASAD_MINIMUM_MAJOR].
 */
object Savings {

    /** Hasad's minimum balance to earn profit, in riyals. */
    const val HASAD_MINIMUM_MAJOR = 5_000L

    private val MC = MathContext.DECIMAL128

    /**
     * The term [today] falls in: the one that started on [SavingsTerms.startDate], or, once it
     * matured and renews, the term after (and so on). Null without a start and tenor.
     */
    fun termOf(terms: SavingsTerms, balanceMinor: Long, today: LocalDate): Term? =
        termOf(terms.startDate, terms.tenorMonths, terms.ratePercent, terms.maturityChoice, balanceMinor, today)

    /** The same for one deposit, whose start is its transfer's day. */
    fun termOf(startDate: LocalDate?, tenorMonths: Int?, ratePercent: String?, choice: MaturityChoice?, balanceMinor: Long, today: LocalDate): Term? {
        var start = startDate ?: return null
        val months = tenorMonths?.takeIf { it > 0 } ?: return null
        val renews = choice != MaturityChoice.PAY_OUT
        while (renews && !start.plusMonths(months.toLong()).isAfter(today)) start = start.plusMonths(months.toLong())
        val maturity = start.plusMonths(months.toLong())
        val days = ChronoUnit.DAYS.between(start, maturity).coerceAtLeast(1)
        val rate = ratePercent?.let(Assets::decimal) ?: BigDecimal.ZERO
        val profit = BigDecimal.valueOf(balanceMinor) * rate.divide(BigDecimal(100), MC) * BigDecimal(months).divide(BigDecimal(12), MC)
        return Term(
            start = start,
            maturity = maturity,
            elapsed = (ChronoUnit.DAYS.between(start, today).toFloat() / days).coerceIn(0f, 1f),
            expectedProfitMinor = profit.setScale(0, RoundingMode.HALF_UP).longValueExact()
        )
    }

    /** Hasad's coming profit: a month at the rate on [lowestMinor], or nothing under the minimum. */
    fun hasadProfit(terms: SavingsTerms, lowestMinor: Long, currencyDigits: Int): Long {
        if (lowestMinor < BigDecimal.valueOf(HASAD_MINIMUM_MAJOR).movePointRight(currencyDigits).toLong()) return 0
        val rate = Assets.decimal(terms.ratePercent) ?: return 0
        return (BigDecimal.valueOf(lowestMinor) * rate.divide(BigDecimal(100), MC)).divide(BigDecimal(12), MC)
            .setScale(0, RoundingMode.HALF_UP).longValueExact()
    }

    /**
     * The lowest the balance was this month: [balanceMinor] now, going back through [flows]
     * (day → that day's net in and out) to the month's first day.
     */
    fun lowestThisMonth(balanceMinor: Long, flows: Map<LocalDate, Long>, today: LocalDate): Long {
        var balance = balanceMinor
        var lowest = balance
        var day = today
        val first = today.withDayOfMonth(1)
        while (!day.isBefore(first)) {
            // The balance at the start of [day] is the end of it, less what moved that day.
            balance -= flows[day] ?: 0
            lowest = minOf(lowest, balance)
            day = day.minusDays(1)
        }
        return lowest
    }
}
