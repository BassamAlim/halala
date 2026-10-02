package bassamalim.halala.features.rules

import bassamalim.halala.core.enums.ExpenseType
import bassamalim.halala.core.enums.RuleSource
import bassamalim.halala.core.models.CategoryOption
import bassamalim.halala.core.utils.DayLabel

data class RulesUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    /** The rules the search leaves, most used first. */
    val rules: List<RuleItem> = emptyList(),
    val categories: List<CategoryOption> = emptyList(),
    /** The rule whose sheet is open. */
    val selected: RuleItem? = null,
    /** Whether that rule's category is being chosen. */
    val isPickingCategory: Boolean = false
)

data class RuleItem(
    val id: Long,
    val merchant: String,
    val categoryId: Long,
    val category: String,
    val expenseType: ExpenseType?,
    val source: RuleSource,
    val enabled: Boolean,
    /** How many transactions it filed, and the day of the latest. */
    val hits: Int,
    val lastHit: DayLabel?
)
