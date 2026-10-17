package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.RetirementPlan
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate

/** What the retirement planner is given. Amounts in minor units; rates as percent a year. */
data class RetirementInputs(
    val ageNow: Int,
    val retireAt: Int,
    val startMinor: Long,
    val monthlyMinor: Long,
    val returnPercent: BigDecimal,
    val inflationPercent: BigDecimal,
    /** The income wanted in retirement, a month, in today's money. */
    val wantedMonthlyMinor: Long
)

/** One year on the growth curve: the age, the pot, and how much of it you put in. */
data class GrowthPoint(val year: Int, val potMinor: Long, val contributedMinor: Long)

data class RetirementResult(
    val potMinor: Long,
    /** The pot in today's money (inflation taken off). */
    val potTodayMinor: Long,
    /** The income a month it supports, in today's money, at a 4% withdrawal rate. */
    val supportsMonthlyMinor: Long,
    /** How far short of the wanted income it falls a month (zero when it doesn't). */
    val shortMonthlyMinor: Long,
    /** The extra saving a month that would close the gap. */
    val extraMonthlyMinor: Long,
    /** The pot the wanted income needs, in money of the day you retire. */
    val neededMinor: Long,
    /** The saving a month, from the pot you have, that reaches [neededMinor]; zero when the pot gets there alone. */
    val neededMonthlyMinor: Long,
    val curve: List<GrowthPoint>
)

data class CompoundResult(val finalMinor: Long, val contributedMinor: Long, val curve: List<GrowthPoint>) {
    val returnsMinor get() = finalMinor - contributedMinor
}

/**
 * The calculators (the spec's compound interest and retirement planner), in exact decimals:
 * monthly saving compounds monthly at the yearly rate over twelve; inflation is taken off by
 * the year; the pot supports what a 4% yearly withdrawal gives. Estimates, rounded to minor units
 * once at the end.
 */
object Planner {

    private val MC = MathContext.DECIMAL128
    private val WITHDRAWAL = BigDecimal("0.04")
    private val TWELVE = BigDecimal(12)
    private val HUNDRED = BigDecimal(100)

    private fun minor(value: BigDecimal) = value.setScale(0, RoundingMode.HALF_UP).longValueExact()

    /** (1 + r)^n, for whole periods. */
    private fun growth(rate: BigDecimal, periods: Int): BigDecimal = (BigDecimal.ONE + rate).pow(periods, MC)

    /** What [start] and [perPeriod] at the end of each period come to after [periods] at [rate] a period. */
    fun futureValue(start: BigDecimal, perPeriod: BigDecimal, rate: BigDecimal, periods: Int): BigDecimal {
        val factor = growth(rate, periods)
        val annuity = if (rate.signum() == 0) BigDecimal(periods) else (factor - BigDecimal.ONE).divide(rate, MC)
        return start * factor + perPeriod * annuity
    }

    /**
     * The planner's inputs for [plan] on [today]: your age is this year less the year you were
     * born; the pot is [startMinor] and the saving [monthlyMinor] (its goal's, as they stand). Null
     * once you have reached the age, or when a rate doesn't read.
     */
    fun inputsOf(plan: RetirementPlan, startMinor: Long, monthlyMinor: Long, today: LocalDate): RetirementInputs? {
        val age = today.year - plan.birthYear
        if (plan.retireAt <= age) return null
        return RetirementInputs(
            ageNow = age,
            retireAt = plan.retireAt,
            startMinor = startMinor,
            monthlyMinor = monthlyMinor,
            returnPercent = plan.returnPercent.toBigDecimalOrNull() ?: return null,
            inflationPercent = plan.inflationPercent.toBigDecimalOrNull() ?: return null,
            wantedMonthlyMinor = plan.wantedMinor
        )
    }

    /** The day a plan's goal is due: the first of the year you reach the age. */
    fun retireOn(plan: RetirementPlan): LocalDate = LocalDate.of(plan.birthYear + plan.retireAt, 1, 1)

    fun retirement(inputs: RetirementInputs): RetirementResult? {
        val years = inputs.retireAt - inputs.ageNow
        if (years <= 0) return null
        val months = years * 12
        val monthlyRate = inputs.returnPercent.divide(HUNDRED, MC).divide(TWELVE, MC)
        val start = BigDecimal.valueOf(inputs.startMinor)
        val monthly = BigDecimal.valueOf(inputs.monthlyMinor)

        val pot = futureValue(start, monthly, monthlyRate, months)
        val inflation = growth(inputs.inflationPercent.divide(HUNDRED, MC), years)
        val potToday = pot.divide(inflation, MC)
        val supports = potToday * WITHDRAWAL / TWELVE
        val wanted = BigDecimal.valueOf(inputs.wantedMonthlyMinor)
        val short = (wanted - supports).max(BigDecimal.ZERO)

        // The pot the wanted income needs, in money of the day you retire, less what is on its way,
        // spread as monthly saving over the same months.
        val needed = wanted * TWELVE / WITHDRAWAL * inflation
        val annuity = if (monthlyRate.signum() == 0) BigDecimal(months) else (growth(monthlyRate, months) - BigDecimal.ONE).divide(monthlyRate, MC)
        val extra = ((needed - pot).max(BigDecimal.ZERO)).divide(annuity, MC)
        val neededMonthly = ((needed - start * growth(monthlyRate, months)).max(BigDecimal.ZERO)).divide(annuity, MC)

        return RetirementResult(
            potMinor = minor(pot),
            potTodayMinor = minor(potToday),
            supportsMonthlyMinor = minor(supports),
            shortMonthlyMinor = minor(short),
            extraMonthlyMinor = minor(extra),
            neededMinor = minor(needed),
            neededMonthlyMinor = minor(neededMonthly),
            curve = (0..years).map { year ->
                GrowthPoint(
                    year = inputs.ageNow + year,
                    potMinor = minor(futureValue(start, monthly, monthlyRate, year * 12)),
                    contributedMinor = minor(start + monthly * BigDecimal(year * 12))
                )
            }
        )
    }

    /**
     * The compound interest calculator: [principalMinor] and [monthlyMinor] a month for [years],
     * at [annualPercent] compounded [perYear] times a year (12, 4 or 1). Monthly saving within a
     * compounding period earns from the next one.
     */
    fun compound(principalMinor: Long, monthlyMinor: Long, annualPercent: BigDecimal, perYear: Int, years: Int): CompoundResult {
        val rate = annualPercent.divide(HUNDRED, MC).divide(BigDecimal(perYear), MC)
        val perPeriod = BigDecimal.valueOf(monthlyMinor) * BigDecimal(12 / perYear)
        val start = BigDecimal.valueOf(principalMinor)
        val curve = (0..years).map { year ->
            GrowthPoint(
                year = year,
                potMinor = minor(futureValue(start, perPeriod, rate, year * perYear)),
                contributedMinor = minor(start + BigDecimal.valueOf(monthlyMinor) * BigDecimal(year * 12))
            )
        }
        return CompoundResult(curve.last().potMinor, curve.last().contributedMinor, curve)
    }
}
