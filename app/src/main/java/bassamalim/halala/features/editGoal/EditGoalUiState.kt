package bassamalim.halala.features.editGoal

import java.time.LocalDate

data class EditGoalUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val form: GoalForm = GoalForm(),
    /** "Mar 2027", or none. */
    val dateLabel: String? = null,
    val accounts: List<AccountChoice> = emptyList(),
    val problems: Set<GoalProblem> = emptySet(),
    val pickingDate: Boolean = false,
    /** The day the picker opens on. */
    val pickFrom: LocalDate = LocalDate.MIN,
    val isConfirmingDelete: Boolean = false
)

data class AccountChoice(val id: Long, val label: String)
