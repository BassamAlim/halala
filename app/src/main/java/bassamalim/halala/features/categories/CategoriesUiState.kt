package bassamalim.halala.features.categories

import bassamalim.halala.core.enums.ExpenseType

data class CategoriesUiState(
    val isLoading: Boolean = true,
    val categories: List<CategoryItem> = emptyList(),
    /** The new category being written, while its sheet is open. */
    val adding: NewCategory? = null,
    /** The category whose removal is being confirmed. */
    val deleting: CategoryItem? = null
)

data class CategoryItem(
    val id: Long,
    val name: String,
    val expenseType: ExpenseType?,
    /** How many transactions are filed under it. */
    val uses: Int
)

data class NewCategory(
    val name: String = "",
    val expenseType: ExpenseType? = null,
    val problem: CategoryProblem? = null
)
