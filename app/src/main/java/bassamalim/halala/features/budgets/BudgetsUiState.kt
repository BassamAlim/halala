package bassamalim.halala.features.budgets

data class BudgetsUiState(
    val isLoading: Boolean = true,
    /** "27 Sep – 26 Oct": the cycle, its last day included. */
    val cycle: String = "",
    val rows: List<BudgetRow> = emptyList()
)
