package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.RecurringSeries
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.enums.CadenceUnit
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.RecurringKind
import bassamalim.halala.core.enums.SeriesStatus
import bassamalim.halala.core.enums.TransactionKind
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** One payment that belongs to a series: when it was, and how much. */
data class Charge(val transactionId: Long, val date: LocalDate, val amountMinor: Long)

/** Where a series stands today. */
data class SeriesState(
    val series: RecurringSeries,
    /** The next day it is due; null once it has ended. */
    val nextDue: LocalDate?,
    /** A linked series whose charge is overdue by more than the grace days. */
    val missed: Boolean,
    /** The last charge, when it cost more than the price you know. */
    val raisedTo: Long?,
    val lastCharge: Charge?,
    /** What it costs over a year and, spread, a month, in minor units. */
    val yearlyMinor: Long,
    val monthlyMinor: Long,
    /** The transactions that are its charges, so nothing else counts them again. */
    val chargeIds: Set<Long> = emptySet()
)

/** What shares of income are taken of, a month: your usual salary or, [fromSalary] false, all that comes in. */
data class IncomeBase(val minor: Long, val fromSalary: Boolean)

/** A heads-up before a charge worth deciding on. */
enum class HeadsUp {
    /** A yearly (or longer) one renews within [Recurring.RENEWAL_LEAD_DAYS] days. */
    RENEWAL,

    /** A subscription's first charge is within [Recurring.FIRST_CHARGE_LEAD_DAYS] days: a free trial ending. */
    FIRST_CHARGE
}

/** A series detection found, for you to confirm. */
data class Proposal(
    val kind: RecurringKind,
    val name: String,
    val merchantId: Long?,
    val personId: Long?,
    val amountMinor: Long,
    val currency: String,
    val every: Int,
    val unit: CadenceUnit,
    val anchor: LocalDate
)

/** Payments that repeat, in one currency, by who they go to: a merchant's charges or transfers to a person. */
data class ChargeGroup(
    val name: String,
    val merchantId: Long?,
    val personId: Long?,
    val currency: String,
    val charges: List<Charge>
)

/**
 * Subscriptions, bills and planned payments: when each is next due, what it costs a month and a
 * year, the alerts (a price rise, a charge that didn't arrive), and finding new ones in history.
 */
object Recurring {

    /** Days past its due date before a missing charge is worth saying. */
    const val GRACE_DAYS = 3L

    const val RENEWAL_LEAD_DAYS = 30L
    const val FIRST_CHARGE_LEAD_DAYS = 7L

    /**
     * The spec's "a free trial about to convert" and "a yearly renewal coming up". A trial is a
     * subscription not charged yet whose first day (its anchor) is close: set the anchor to the
     * day the trial ends. Nothing once you asked to be reminded to cancel it: you have decided.
     */
    fun headsUp(state: SeriesState, today: LocalDate): HeadsUp? {
        val series = state.series
        val due = state.nextDue ?: return null
        if (series.status != SeriesStatus.ACTIVE || series.cancelReminder || due.isBefore(today)) return null
        val days = ChronoUnit.DAYS.between(today, due)
        return when {
            series.kind == RecurringKind.SUBSCRIPTION && state.lastCharge == null && due == series.anchor &&
                    days <= FIRST_CHARGE_LEAD_DAYS -> HeadsUp.FIRST_CHARGE
            series.kind != RecurringKind.PLANNED && cadenceDays(series.every, series.unit) >= YEARLY_DAYS &&
                    days <= RENEWAL_LEAD_DAYS -> HeadsUp.RENEWAL
            else -> null
        }
    }

    /** Remembers "keep it" for one heads-up: the series and the day it was due. */
    fun headsUpKey(state: SeriesState) = "series:${state.series.id}:${state.nextDue}"

    /** Twelve months count as yearly though [cadenceDays] makes them 360. */
    private const val YEARLY_DAYS = 360L

    /** The [k]th occurrence from [anchor], counted from the anchor so months never drift. */
    fun occurrence(anchor: LocalDate, every: Int, unit: CadenceUnit, k: Long): LocalDate {
        val n = Math.multiplyExact(k, every.toLong())
        return when (unit) {
            CadenceUnit.DAY -> anchor.plusDays(n)
            CadenceUnit.WEEK -> anchor.plusWeeks(n)
            CadenceUnit.MONTH -> anchor.plusMonths(n)
            CadenceUnit.YEAR -> anchor.plusYears(n)
        }
    }

    /** About how many days one cadence is (for windows; a month counts 30). */
    fun cadenceDays(every: Int, unit: CadenceUnit): Long = every.toLong() * when (unit) {
        CadenceUnit.DAY -> 1
        CadenceUnit.WEEK -> 7
        CadenceUnit.MONTH -> 30
        CadenceUnit.YEAR -> 365
    }

    /** What [amountMinor] every [every] [unit] comes to in a year, rounded half up. */
    fun yearly(amountMinor: Long, every: Int, unit: CadenceUnit): Long {
        val perYear = when (unit) {
            CadenceUnit.DAY -> 365
            CadenceUnit.WEEK -> 52
            CadenceUnit.MONTH -> 12
            CadenceUnit.YEAR -> 1
        }
        return BigDecimal.valueOf(amountMinor).multiply(BigDecimal.valueOf(perYear.toLong()))
            .divide(BigDecimal.valueOf(every.toLong()), 0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    /** A year spread over twelve months, rounded half up. */
    fun monthly(yearlyMinor: Long): Long =
        BigDecimal.valueOf(yearlyMinor).divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP).longValueExact()

    /**
     * Where [series] stands on [today], given its [charges]. A series with a payee (a merchant or
     * a person) moves on one occurrence for each charge from its anchor on (a charge up to half a
     * cadence early counts). One without a payee is due after the occurrence its last payment
     * linked by hand covers or, with none, is taken as paid when its day passes.
     */
    fun stateOf(series: RecurringSeries, charges: List<Charge>, today: LocalDate): SeriesState {
        val byPayee = series.merchantId != null || series.personId != null
        val half = cadenceDays(series.every, series.unit) / 2
        val counted = charges.filter { !it.date.isBefore(series.anchor.minusDays(half)) }
        // Without a payee, the charges are only the payments you linked by hand: once there is
        // one, it is expected each time, as a payee's charge is.
        val linked = byPayee || counted.isNotEmpty()

        fun firstFrom(date: LocalDate): LocalDate {
            var k = 0L
            while (occurrence(series.anchor, series.every, series.unit, k).isBefore(date)) k++
            return occurrence(series.anchor, series.every, series.unit, k)
        }

        val next = when {
            byPayee -> occurrence(series.anchor, series.every, series.unit, counted.size.toLong())
            // The last payment covers the first occurrence from half a cadence before it; the next is due after that.
            linked -> firstFrom(counted.maxOf { it.date }.minusDays(half)).let { covered ->
                firstFrom(covered.plusDays(1))
            }
            else -> firstFrom(today)
        }
        val ended = series.endsOn?.let { next.isAfter(it) } ?: false
        val last = charges.maxWithOrNull(compareBy({ it.date }, { it.transactionId }))
        val yearly = yearly(series.amountMinor, series.every, series.unit)

        return SeriesState(
            series = series,
            nextDue = next.takeUnless { ended },
            missed = linked && !ended && next.isBefore(today.minusDays(GRACE_DAYS)),
            raisedTo = last?.amountMinor?.takeIf { it > series.amountMinor },
            lastCharge = last,
            yearlyMinor = if (ended) 0 else yearly,
            monthlyMinor = if (ended) 0 else monthly(yearly),
            chargeIds = charges.mapTo(mutableSetOf()) { it.transactionId }
        )
    }

    /**
     * Payments that repeat: the same payee, three times or more, at a steady interval (a week, a
     * month or a year) and a similar amount, the last one recent. Each becomes a proposal, due
     * one cadence after its last charge: a subscription when the amount barely moves, a bill
     * when it moves some (utilities), a planned payment for money to a person.
     */
    fun detect(groups: List<ChargeGroup>, today: LocalDate): List<Proposal> = groups.mapNotNull { group ->
        val byDay = group.charges.groupBy { it.date }.map { (_, same) -> same.maxBy { it.amountMinor } }.sortedBy { it.date }
        if (byDay.size < MIN_CHARGES) return@mapNotNull null
        val recent = byDay.takeLast(LOOK_BACK)
        val gaps = recent.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.date, b.date) }
        val unit = CADENCES.entries.firstOrNull { (_, range) -> gaps.all { it in range } }?.key ?: return@mapNotNull null

        val last = recent.last()
        if (last.date.plusDays(2 * cadenceDays(1, unit)).isBefore(today)) return@mapNotNull null

        val amounts = recent.takeLast(MIN_CHARGES).map { it.amountMinor }
        val steady = amounts.max() * 100 <= amounts.min() * 105
        val close = amounts.max() * 100 <= amounts.min() * 150
        val kind = when {
            group.personId != null -> if (steady) RecurringKind.PLANNED else return@mapNotNull null
            steady -> RecurringKind.SUBSCRIPTION
            close -> RecurringKind.BILL
            else -> return@mapNotNull null
        }
        Proposal(
            kind = kind,
            name = group.name,
            merchantId = group.merchantId,
            personId = group.personId,
            amountMinor = last.amountMinor,
            currency = group.currency,
            every = 1,
            unit = unit,
            anchor = occurrence(last.date, 1, unit, 1)
        )
    }

    /** How many full months [monthlyIncome] averages over. */
    const val INCOME_MONTHS = 3L

    /**
     * What comes in a month in [currency], on average: income as totals count it, over the last
     * [INCOME_MONTHS] full calendar months, not counting the month records began in (it is
     * likely partial). Null until there is a full month, or when nothing came in.
     */
    fun monthlyIncome(details: List<TransactionDetail>, zone: ZoneId, today: LocalDate, currency: String): Long? {
        fun monthOf(detail: TransactionDetail) = YearMonth.from(detail.transaction.occurredAt.atZone(zone))
        val first = details.minOfOrNull { it.transaction.occurredAt }?.atZone(zone)?.let(YearMonth::from) ?: return null
        val thisMonth = YearMonth.from(today)
        val months = (1..INCOME_MONTHS).map { thisMonth.minusMonths(it) }.filter { it.isAfter(first) }.toSet()
        if (months.isEmpty()) return null
        val income = inOut(details.filter { monthOf(it) in months }, currency).inMinor
        if (income <= 0) return null
        return BigDecimal.valueOf(income).divide(BigDecimal.valueOf(months.size.toLong()), 0, RoundingMode.HALF_UP).longValueExact()
    }

    /**
     * What fixed costs are a share of, in [currency]: your usual salary (the pay cycle's, the
     * median of the last three pay days) while one is current; otherwise, with no salary or
     * none for [PayCycles.STALE_DAYS], [monthlyIncome]. Null when neither is known.
     */
    fun incomeBase(details: List<TransactionDetail>, zone: ZoneId, today: LocalDate, currency: String): IncomeBase? {
        val cycle = PayCycles.current(Budgets.salaries(details.filter { it.transaction.currency == currency }, zone), today)
        cycle.salaryMinor?.takeIf { cycle.fromSalary && it > 0 }?.let { return IncomeBase(it, fromSalary = true) }
        return monthlyIncome(details, zone, today, currency)?.let { IncomeBase(it, fromSalary = false) }
    }

    private const val MIN_CHARGES = 3
    private const val LOOK_BACK = 6

    /** The gaps, in days, that read as each cadence. */
    private val CADENCES = linkedMapOf(
        CadenceUnit.WEEK to 6L..8L,
        CadenceUnit.MONTH to 26L..35L,
        CadenceUnit.YEAR to 355L..376L
    )
}

/** Which charges are whose, from the feed's transactions. */
object Charges {

    /** Money out that a merchant or a person repeats: purchases and bills, and transfers to people. */
    private fun TransactionDetail.isCharge() =
        transaction.direction == Direction.DEBIT && !isInternalTransfer &&
                transaction.kind in setOf(TransactionKind.PURCHASE, TransactionKind.BILL_PAYMENT, TransactionKind.TRANSFER_OUT)

    private fun TransactionDetail.toCharge(zone: ZoneId) =
        Charge(transaction.id, transaction.occurredAt.atZone(zone).toLocalDate(), transaction.amountMinor)

    /** Money out that can be linked by hand as a payment of a series: anything spent or sent, but a move. */
    fun canPay(detail: TransactionDetail) =
        detail.transaction.direction == Direction.DEBIT && !detail.isInternalTransfer && detail.transaction.kind.countsInTotals

    /**
     * The charges of [series], in its currency: the payments linked to it by hand ([paidTo],
     * transaction id → series id), and its merchant's (or person's) that aren't linked to another.
     */
    fun of(
        series: RecurringSeries,
        details: List<TransactionDetail>,
        zone: ZoneId,
        paidTo: Map<Long, Long> = emptyMap()
    ): List<Charge> = details
        .filter { detail ->
            val paid = paidTo[detail.transaction.id]
            detail.transaction.currency == series.currency && when (paid) {
                null -> detail.isCharge() &&
                        ((series.merchantId != null && detail.merchantId == series.merchantId && detail.personId == null) ||
                                (series.personId != null && detail.personId == series.personId))
                else -> paid == series.id && canPay(detail)
            }
        }
        .map { it.toCharge(zone) }

    /** Every payee's charges, by merchant or person and currency, for detection; [paid] ones already belong to a series. */
    fun groups(details: List<TransactionDetail>, zone: ZoneId, paid: Set<Long> = emptySet()): List<ChargeGroup> {
        val charges = details.filter { it.isCharge() && it.transaction.id !in paid }
        val byPerson = charges.filter { it.personId != null }
            .groupBy { it.personId!! to it.transaction.currency }
            .map { (key, list) -> ChargeGroup(list.first().personName.orEmpty(), null, key.first, key.second, list.map { it.toCharge(zone) }) }
        val byMerchant = charges.filter { it.personId == null && it.merchantId != null }
            .groupBy { it.merchantId!! to it.transaction.currency }
            .map { (key, list) -> ChargeGroup(list.first().merchantName.orEmpty(), key.first, null, key.second, list.map { it.toCharge(zone) }) }
        return byMerchant + byPerson
    }
}

/** What active subscriptions and bills cost a month together, in one currency. */
object RecurringDomainTotals {
    fun monthly(states: List<SeriesState>, currency: String): Long = Money.sum(
        states.filter { it.series.status == SeriesStatus.ACTIVE && it.series.currency == currency }
            .map { it.monthlyMinor }
    )
}
