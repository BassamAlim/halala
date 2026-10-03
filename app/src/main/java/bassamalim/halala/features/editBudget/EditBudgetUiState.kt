package bassamalim.halala.features.editBudget

import bassamalim.halala.core.models.CategoryOption

data class EditBudgetUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val form: BudgetForm = BudgetForm(),
    val categories: List<CategoryOption> = emptyList(),
    /** The busiest merchants matching [merchantQuery], to choose from. */
    val merchants: List<CategoryOption> = emptyList(),
    val merchantQuery: String = "",
    val tags: List<CategoryOption> = emptyList(),
    val problems: Set<BudgetProblem> = emptySet(),
    val isConfirmingDelete: Boolean = false
)
