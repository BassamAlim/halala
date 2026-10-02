package bassamalim.halala.features.categories

import bassamalim.halala.core.enums.BusinessType
import bassamalim.halala.core.enums.ExpenseType

data class CategoriesUiState(
    val isLoading: Boolean = true,
    val categories: List<CategoryItem> = emptyList(),
    /** The category being added or changed, while its sheet is open. */
    val form: CategoryForm? = null,
    /** The category whose removal is being confirmed. */
    val deleting: CategoryItem? = null
)

data class CategoryItem(
    val id: Long,
    val name: String,
    val expenseType: ExpenseType?,
    val businessTypes: List<BusinessType>,
    /** How many transactions are filed under it. */
    val uses: Int
)

/** A category as it is being written: a new one has no [id]. */
data class CategoryForm(
    val id: Long? = null,
    val name: String = "",
    val expenseType: ExpenseType? = null,
    val businessTypes: List<BusinessType> = emptyList(),
    val problem: CategoryProblem? = null
)
