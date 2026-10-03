package bassamalim.halala.features.editGoal

import bassamalim.halala.core.utils.OneAtATime
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.accountLabel
import bassamalim.halala.core.utils.monthYearLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class EditGoalViewModel @Inject constructor(
    private val domain: EditGoalDomain,
    private val navigator: Navigator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val saving = OneAtATime()

    private val id = savedStateHandle.toRoute<Screen.EditGoal>().id

    private val form = MutableStateFlow(if (id == 0L) GoalForm() else null)
    private val problems = MutableStateFlow(emptySet<GoalProblem>())
    private val picking = MutableStateFlow(false)
    private val confirmingDelete = MutableStateFlow(false)

    init {
        if (id != 0L) viewModelScope.launch { form.value = domain.load(id) ?: GoalForm() }
    }

    val uiState: StateFlow<EditGoalUiState> = combine(
        form, problems, domain.observeAccounts(), picking, confirmingDelete
    ) { form, problems, accounts, picking, confirming ->
        val today = domain.today()
        EditGoalUiState(
            isLoading = form == null,
            isNew = id == 0L,
            form = form ?: GoalForm(),
            dateLabel = form?.targetDate?.let(::monthYearLabel),
            accounts = accounts
                .filter { (!it.account.archived && it.account.type.listed) || it.account.id in form?.accountIds.orEmpty() }
                .map { AccountChoice(it.account.id, accountLabel(it.institutionName, it.account.nickname)) },
            problems = problems,
            pickingDate = picking,
            pickFrom = form?.targetDate ?: today.plusYears(1),
            isConfirmingDelete = confirming
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditGoalUiState())

    fun onBackClick() = navigator.popBackStack()

    fun onNameChange(text: String) = edit { it.copy(name = text) }

    fun onTargetChange(text: String) = edit { it.copy(target = text) }

    fun onAccountClick(accountId: Long) = edit {
        it.copy(accountIds = if (accountId in it.accountIds) it.accountIds - accountId else it.accountIds + accountId)
    }

    fun onDateClick() = picking.update { true }

    fun onDatePicked(date: LocalDate) {
        picking.update { false }
        edit { it.copy(targetDate = date) }
    }

    fun onDateDismiss() = picking.update { false }

    fun onDateClear() = edit { it.copy(targetDate = null) }

    fun onDeleteClick() = confirmingDelete.update { true }

    fun onDeleteDismiss() = confirmingDelete.update { false }

    fun onDeleteConfirm() {
        confirmingDelete.update { false }
        viewModelScope.launch {
            domain.delete(id)
            navigator.popBackStack()
        }
    }

    fun onSaveClick() {
        val current = form.value ?: return
        saving.launch(viewModelScope) {
            val found = domain.save(id, current)
            problems.update { found }
            if (found.isEmpty()) navigator.popBackStack()
            found.isEmpty()
        }
    }

    private fun edit(change: (GoalForm) -> GoalForm) {
        form.update { it?.let(change) }
        problems.update { emptySet() }
    }
}
