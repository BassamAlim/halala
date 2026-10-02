package bassamalim.halala.core.enums

/**
 * What a transaction is, which decides which screens and totals include it (the spec's list,
 * plus [ADJUSTMENT] for a cash reconcile that finds more than was recorded, and [OTHER]).
 */
enum class TransactionKind {
    PURCHASE,
    REFUND,
    TRANSFER_OUT,
    TRANSFER_IN,
    INTERNAL_TRANSFER,
    SALARY,
    ATM_WITHDRAWAL,
    CASH_DEPOSIT,
    FEE,
    BILL_PAYMENT,
    INVESTMENT_BUY,
    INVESTMENT_SELL,
    SAVINGS_DEPOSIT,
    SAVINGS_WITHDRAWAL,
    LOAN_GIVEN,
    LOAN_RECEIVED,
    LOAN_REPAYMENT,
    ADJUSTMENT,
    OTHER;

    /**
     * Whether it counts in spending and income. Moving your own money (between accounts, into
     * savings or funds) and correcting a count are neither; a paired internal transfer is excluded as well, whatever its kind.
     */
    val countsInTotals get() = this !in NOT_IN_TOTALS

    companion object {
        // Putting money into savings or funds, or taking it back, moves it; it isn't spent or earned.
        private val NOT_IN_TOTALS = setOf(
            INTERNAL_TRANSFER, ATM_WITHDRAWAL, CASH_DEPOSIT, ADJUSTMENT,
            SAVINGS_DEPOSIT, SAVINGS_WITHDRAWAL, INVESTMENT_BUY, INVESTMENT_SELL
        )

        /** The kinds offered when entering money out by hand. */
        val MANUAL_OUT = listOf(PURCHASE, BILL_PAYMENT, FEE, TRANSFER_OUT, OTHER)

        /** The kinds offered when entering money in by hand. */
        val MANUAL_IN = listOf(SALARY, TRANSFER_IN, REFUND, OTHER)

        /**
         * A move between two of your own accounts: taking cash out is an ATM withdrawal,
         * putting it in is a cash deposit, anything else is an internal transfer.
         */
        fun forMove(fromCash: Boolean, toCash: Boolean): TransactionKind = when {
            toCash && !fromCash -> ATM_WITHDRAWAL
            fromCash && !toCash -> CASH_DEPOSIT
            else -> INTERNAL_TRANSFER
        }
    }
}
