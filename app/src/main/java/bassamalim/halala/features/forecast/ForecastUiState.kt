package bassamalim.halala.features.forecast

import java.time.LocalDate

data class ForecastUiState(
    val isLoading: Boolean = true,
    /** Null until there is a cycle's spending to go on. */
    val end: String? = null,
    val low: String = "",
    val high: String = "",
    /** The window the salary is expected in: "26", "28 Oct". */
    val salaryFrom: String? = null,
    val salaryTo: String? = null,
    val chart: ForecastChart? = null,
    val months: List<MonthBar> = emptyList(),
    /** The biggest payments in the month that leaves least, when it dips below the others. */
    val dip: Dip? = null,
    val afford: AffordForm = AffordForm()
)

/**
 * The cycle's balance, in minor units: [past] from the cycle's start to today, [future] from
 * today to its end (both include today), and the band at the end. Labels are already words.
 */
data class ForecastChart(
    val past: List<Long>,
    val future: List<Long>,
    val endLow: Long,
    val endHigh: Long,
    val startLabel: String,
    val endLabel: String
)

/** One month ahead: "Nov", "+3,900", and how tall against the largest (0 to 1). */
data class MonthBar(val label: String, val left: String, val negative: Boolean, val fraction: Float)

/** "January dips: car insurance 2,400 and Istimara renewal." */
data class Dip(val month: String, val items: List<Pair<String, String>>)

/** "Can I afford it?": what you typed, the day, and the answer once there is one. */
data class AffordForm(
    val amount: String = "",
    val on: LocalDate? = null,
    val onLabel: String = "",
    val picking: Boolean = false,
    /** The day the picker opens on: the chosen one, else today. */
    val pickFrom: LocalDate = LocalDate.MIN,
    val result: AffordResult? = null
)

data class AffordResult(val affordable: Boolean, val lowest: String, val on: String, val overBudget: Boolean)
