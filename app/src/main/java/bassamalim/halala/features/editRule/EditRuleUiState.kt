package bassamalim.halala.features.editRule

import bassamalim.halala.core.models.CategoryOption

data class EditRuleUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val form: RuleForm = RuleForm(),
    val accounts: List<AccountChoice> = emptyList(),
    val categories: List<CategoryOption> = emptyList(),
    /** Shown once Save has been tried. */
    val problems: Set<RuleProblem> = emptySet()
)

data class AccountChoice(val id: Long, val label: String)
