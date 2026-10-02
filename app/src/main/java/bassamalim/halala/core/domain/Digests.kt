package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.enums.Direction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** How often a digest looks back. */
enum class DigestKind { WEEK, MONTH, YEAR }

/** A digest's span: from [start] up to, not including, [end]. */
data class DigestPeriod(val kind: DigestKind, val start: LocalDate, val end: LocalDate) {
    fun contains(date: LocalDate) = !date.isBefore(start) && date.isBefore(end)

    /** The period before this one. */
    fun previous(): DigestPeriod = Digests.periodOf(kind, start.minusDays(1))
}

/** Something worth a look in a digest, as data for the screen to put into words. */
sealed interface Observation {
    /** A category's spending moved by [percent] (signed) against its average over the three periods before. */
    data class CategoryMoved(val category: String, val percent: Int) : Observation

    /** A subscription or bill went up from [fromMinor] to [toMinor]; together they now cost [monthlyMinor] a month. */
    data class PriceRose(val name: String, val fromMinor: Long, val toMinor: Long, val monthlyMinor: Long) : Observation

    /** A loan with [person] falls due on [due]; owed to you when [lent]. */
    data class LoanDue(val person: String, val due: LocalDate, val lent: Boolean) : Observation
}

/** One digest: spending against the period before, income, what was left, where it went, what is worth a look. */
data class Digest(
    val period: DigestPeriod,
    val spentMinor: Long,
    val previousSpentMinor: Long,
    /** Spending against the period before, in whole percent (signed); null when there was none before. */
    val changePercent: Int?,
    val incomeMinor: Long,
    /** Income less spending: can be negative. */
    val savedMinor: Long,
    val owedToYouMinor: Long,
    /** The biggest categories, by name (null for spending not yet filed), the biggest first. */
    val categories: List<Pair<String?, Long>>,
    val observations: List<Observation>
)

/**
 * Weekly, monthly and yearly digests, built from the ledger as it is: nothing is stored, so a
 * past digest reads the same whenever it is opened (until what it covers is changed). Weeks
 * run Sunday to Saturday, as the Saudi week does.
 */
object Digests {

    const val TOP_CATEGORIES = 5
    const val MAX_OBSERVATIONS = 3

    /** A category must move at least this much, and by at least [MOVE_FLOOR_MINOR], to be worth a look. */
    private const val MOVE_PERCENT = 25
    private const val MOVE_FLOOR_MINOR = 10_000L

    /** The period of [kind] that [date] falls in. */
    fun periodOf(kind: DigestKind, date: LocalDate): DigestPeriod = when (kind) {
        DigestKind.WEEK -> {
            val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            DigestPeriod(kind, start, start.plusWeeks(1))
        }
        DigestKind.MONTH -> {
            val start = date.withDayOfMonth(1)
            DigestPeriod(kind, start, start.plusMonths(1))
        }
        DigestKind.YEAR -> {
            val start = date.withDayOfYear(1)
            DigestPeriod(kind, start, start.plusYears(1))
        }
    }

    /** The last [count] finished periods of [kind] before [today], newest first. */
    fun finished(kind: DigestKind, today: LocalDate, count: Int): List<DigestPeriod> =
        generateSequence(periodOf(kind, today).previous()) { it.previous() }.take(count).toList()

    /** Your spending in [period], by category id (null for not yet filed). */
    fun spendingByCategory(details: List<TransactionDetail>, period: DigestPeriod, currency: String, zone: ZoneId): Map<Long?, Long> =
        details
            .filter { Budgets.isSpending(it) && it.transaction.currency == currency }
            .filter { period.contains(it.transaction.occurredAt.atZone(zone).toLocalDate()) }
            .groupBy { it.transaction.categoryId }
            .mapValues { (_, list) -> Money.sum(list.map { it.yourMinor }) }

    fun income(details: List<TransactionDetail>, period: DigestPeriod, currency: String, zone: ZoneId): Long = Money.sum(
        details
            .filter {
                it.transaction.direction == Direction.CREDIT && it.transaction.kind.countsInTotals && !it.isInternalTransfer &&
                        it.transaction.currency == currency && period.contains(it.transaction.occurredAt.atZone(zone).toLocalDate())
            }
            .map { it.transaction.amountMinor }
    )

    /** Whole percent [now] is above (or below) [before], rounded half up; null when [before] is nothing. */
    fun percent(now: Long, before: Long): Int? = if (before <= 0) null else
        BigDecimal.valueOf(now - before).multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(before), 0, RoundingMode.HALF_UP).toInt()

    fun build(
        period: DigestPeriod,
        details: List<TransactionDetail>,
        categoryNames: Map<Long, String>,
        currency: String,
        zone: ZoneId,
        owedToYouMinor: Long,
        priceRises: List<Observation.PriceRose>,
        loansDue: List<Observation.LoanDue>
    ): Digest {
        val now = spendingByCategory(details, period, currency, zone)
        val spent = Money.sum(now.values)
        val previous = Money.sum(spendingByCategory(details, period.previous(), currency, zone).values)
        val income = income(details, period, currency, zone)

        // Each category against its average over the three periods before.
        val history = generateSequence(period.previous()) { it.previous() }.take(3)
            .map { spendingByCategory(details, it, currency, zone) }.toList()
        val moved = now.mapNotNull { (categoryId, amount) ->
            val name = categoryId?.let(categoryNames::get) ?: return@mapNotNull null
            val average = Money.sum(history.map { it[categoryId] ?: 0 }) / 3
            val change = percent(amount, average) ?: return@mapNotNull null
            if (kotlin.math.abs(change) >= MOVE_PERCENT && kotlin.math.abs(amount - average) >= MOVE_FLOOR_MINOR)
                Observation.CategoryMoved(name, change) to kotlin.math.abs(amount - average)
            else null
        }.sortedByDescending { it.second }.map { it.first }

        return Digest(
            period = period,
            spentMinor = spent,
            previousSpentMinor = previous,
            changePercent = percent(spent, previous),
            incomeMinor = income,
            savedMinor = income - spent,
            owedToYouMinor = owedToYouMinor,
            categories = now.entries.sortedByDescending { it.value }.take(TOP_CATEGORIES)
                .map { (id, amount) -> id?.let(categoryNames::get) to amount },
            observations = (moved + priceRises + loansDue).take(MAX_OBSERVATIONS)
        )
    }
}
