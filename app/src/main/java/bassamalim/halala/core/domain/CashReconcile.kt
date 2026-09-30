package bassamalim.halala.core.domain

/**
 * Counting the wallet. Whatever you count is the truth; the ledger is corrected to it by one
 * transaction for the gap.
 */
sealed interface CashGap {

    /** The wallet matches the ledger: nothing to record. */
    data object None : CashGap

    /** Less in the wallet than recorded: that money was spent, on what is unknown. */
    data class Spent(val amountMinor: Long) : CashGap

    /** More in the wallet than recorded: an adjustment, not income. */
    data class Found(val amountMinor: Long) : CashGap

    companion object {

        fun of(recordedMinor: Long, countedMinor: Long): CashGap {
            val gap = Math.subtractExact(countedMinor, recordedMinor)

            return when {
                gap < 0 -> Spent(Math.negateExact(gap))
                gap > 0 -> Found(gap)
                else -> None
            }
        }

        /** What the gap is called in the ledger: "cash spending, uncategorised" per the spec. */
        const val SPENT_TITLE = "Cash spending"
        const val FOUND_TITLE = "Cash counted"
    }
}
