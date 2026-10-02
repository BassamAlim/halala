package bassamalim.halala.features.rules

import bassamalim.halala.core.enums.ExpenseType
import bassamalim.halala.core.enums.RuleSource
import bassamalim.halala.core.models.RuleWords
import bassamalim.halala.core.utils.DayLabel

data class RulesUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    /** The rules the search leaves, most used first. */
    val rules: List<RuleItem> = emptyList(),
    /** The rule whose sheet is open. */
    val selected: RuleItem? = null
)

data class RuleItem(
    val id: Long,
    val words: RuleWords,
    val category: String,
    val expenseType: ExpenseType?,
    val source: RuleSource,
    val enabled: Boolean,
    /** How many transactions it filed, and the day of the latest. */
    val hits: Int,
    val lastHit: DayLabel?
)
