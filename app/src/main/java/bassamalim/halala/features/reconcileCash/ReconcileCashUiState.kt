package bassamalim.halala.features.reconcileCash

data class ReconcileCashUiState(
    val isLoading: Boolean = true,
    /** What the ledger says is in the wallet: "1,240.00". */
    val recorded: String = "",
    val currency: String = "",
    val counted: String = "",
    /** What saving would record, once the count is an amount. */
    val outcome: Outcome? = null,
    val isInvalid: Boolean = false
)

sealed interface Outcome {
    data object Matches : Outcome
    /** "62.00" less than recorded, to be recorded as cash spending. */
    data class Spent(val amount: String) : Outcome
    /** "20.00" more than recorded, to be recorded as a correction. */
    data class Found(val amount: String) : Outcome
}
