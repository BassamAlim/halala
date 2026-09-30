package bassamalim.halala.core.domain

import java.math.BigInteger

/**
 * How spending stands against a budget, which picks the balance card's fill and a progress bar's
 * colour: under 80% is on track, 80–100% is spending fast, over 100% is over budget.
 */
enum class BudgetState {
    OK,
    WARN,
    OVER;

    companion object {

        /** Exact, in integers: 80% is compared as spent × 100 against budget × 80. */
        fun of(spentMinor: Long, budgetMinor: Long): BudgetState {
            if (budgetMinor <= 0) return if (spentMinor > 0) OVER else OK

            val spent = BigInteger.valueOf(spentMinor)
            val budget = BigInteger.valueOf(budgetMinor)

            return when {
                spent * HUNDRED < budget * WARN_PERCENT -> OK
                spent <= budget -> WARN
                else -> OVER
            }
        }

        /** How full the bar is, 0 to 1. A fraction of a bar is display, not money. */
        fun progress(spentMinor: Long, budgetMinor: Long): Float {
            if (budgetMinor <= 0) return if (spentMinor > 0) 1f else 0f

            return (spentMinor.toDouble() / budgetMinor.toDouble()).toFloat().coerceIn(0f, 1f)
        }

        private val HUNDRED = BigInteger.valueOf(100)
        private val WARN_PERCENT = BigInteger.valueOf(80)
    }
}
