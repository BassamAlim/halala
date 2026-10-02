package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.ZakatProfile
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit

/** Where zakat stands: what counts, the nisab, what is due and when. Minor units in one currency. */
data class ZakatState(
    val parts: Map<WealthClass, Long>,
    val debtsMinor: Long,
    /** What counts, less debts due now; never below zero. */
    val zakatableMinor: Long,
    /** 85 g of gold at today's price; null without a price. */
    val nisabMinor: Long?,
    /** 2.5% of what counts, when it reaches the nisab; zero under it; null without a nisab. */
    val dueMinor: Long?,
    /** The next zakat day (on or after today), in both calendars; null until you set yours. */
    val dueOn: LocalDate?,
    val dueHijri: HijrahDate?,
    val hawlElapsedDays: Long,
    val hawlDays: Long,
    /** Paid already for the hawl ending on [dueOn]. */
    val paid: Boolean
)

/**
 * The zakat calculator (the spec's): what counts is chosen by you, debts due now come off, the
 * nisab is 85 g of gold, the rate 2.5%, on a Hijri year in the Umm al-Qura calendar (Java's
 * Hijrah chronology). It shows its method; it doesn't rule.
 */
object Zakat {

    const val NISAB_GRAMS = 85

    private val RATE = BigDecimal("0.025")

    /** The classes of wealth that can count, in the board's order. */
    val COUNTED = listOf(WealthClass.ACCOUNTS, WealthClass.SAVINGS, WealthClass.FUNDS, WealthClass.GOLD, WealthClass.OWED_TO_YOU)

    fun included(profile: ZakatProfile, kind: WealthClass): Boolean = when (kind) {
        WealthClass.ACCOUNTS -> profile.includeAccounts
        WealthClass.SAVINGS -> profile.includeSavings
        WealthClass.FUNDS -> profile.includeFunds
        WealthClass.GOLD -> profile.includeGold
        WealthClass.OWED_TO_YOU -> profile.includeOwed
        else -> false
    }

    /** [minor] rounded half up in [scale]-decimal units: a percentage or price times an amount. */
    private fun round(value: BigDecimal, scale: Int): Long = value.setScale(0, RoundingMode.HALF_UP).longValueExact()

    fun stateOf(
        profile: ZakatProfile,
        netWorth: NetWorthNow,
        goldPricePerGram: BigDecimal?,
        currencyDigits: Int,
        today: LocalDate
    ): ZakatState {
        val parts = COUNTED.associateWith { netWorth.parts[it] ?: 0L }
        val counted = Money.sum(parts.filter { (kind, value) -> included(profile, kind) && value > 0 }.values)
        val debts = Math.addExact(-(netWorth.parts[WealthClass.YOU_OWE] ?: 0), profile.otherDebtsMinor)
        val zakatable = maxOf(0, counted - debts)
        val nisab = goldPricePerGram?.let { round(it.multiply(BigDecimal(NISAB_GRAMS)).movePointRight(currencyDigits), currencyDigits) }
        val due = nisab?.let { if (zakatable >= it) round(BigDecimal.valueOf(zakatable).multiply(RATE), 0) else 0 }

        val next = profile.hijriMonth?.let { month -> profile.hijriDay?.let { day -> nextOn(month, day, today) } }
        val previous = next?.minus(1, ChronoUnit.YEARS)
        val nextDay = next?.let(LocalDate::from)
        val previousDay = previous?.let(LocalDate::from)
        return ZakatState(
            parts = parts,
            debtsMinor = debts,
            zakatableMinor = zakatable,
            nisabMinor = nisab,
            dueMinor = due,
            dueOn = nextDay,
            dueHijri = next,
            hawlElapsedDays = if (previousDay != null) ChronoUnit.DAYS.between(previousDay, today).coerceAtLeast(0) else 0,
            hawlDays = if (previousDay != null && nextDay != null) ChronoUnit.DAYS.between(previousDay, nextDay) else 0,
            paid = next != null && profile.paidHijriYear == next.get(ChronoField.YEAR)
        )
    }

    /** The next [month]/[day] in the Hijri calendar on or after [today]; a day past a short month's end is its last day. */
    fun nextOn(month: Int, day: Int, today: LocalDate): HijrahDate {
        val now = HijrahDate.from(today)
        var year = now.get(ChronoField.YEAR)
        while (true) {
            val first = HijrahChronology.INSTANCE.date(year, month, 1)
            val candidate = first.plus((minOf(day, first.lengthOfMonth()) - 1).toLong(), ChronoUnit.DAYS)
            if (!LocalDate.from(candidate).isBefore(today)) return candidate
            year++
        }
    }
}
