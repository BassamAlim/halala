package bassamalim.halala.features.editRecurring

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import bassamalim.halala.core.enums.CadenceUnit
import bassamalim.halala.core.enums.RecurringKind
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.shortDateLabel
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
class EditRecurringViewModel @Inject constructor(
    private val domain: EditRecurringDomain,
    private val navigator: Navigator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val id = savedStateHandle.toRoute<Screen.EditRecurring>().id

    /** Null until an existing one has been read. */
    private val form = MutableStateFlow(if (id == 0L) SeriesForm(nextDue = domain.today()) else null)
    private val problems = MutableStateFlow(emptySet<SeriesProblem>())
    private val sheet = MutableStateFlow<EditRecurringSheet?>(null)

    init {
        if (id != 0L) viewModelScope.launch {
            form.value = domain.load(id) ?: SeriesForm(nextDue = domain.today())
        }
    }

    val uiState: StateFlow<EditRecurringUiState> = combine(form, problems, sheet) { form, problems, sheet ->
        val today = domain.today()
        EditRecurringUiState(
            isLoading = form == null,
            isNew = id == 0L,
            form = form ?: SeriesForm(),
            dueLabel = form?.let { shortDateLabel(it.nextDue, today) }.orEmpty(),
            endsLabel = form?.endsOn?.let { shortDateLabel(it, today) },
            problems = problems,
            sheet = sheet
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EditRecurringUiState()
    )

    fun onBackClick() = navigator.popBackStack()

    fun onNameChange(text: String) = edit { it.copy(name = text) }

    /** A subscription renews on its own unless you say otherwise. */
    fun onKindClick(kind: RecurringKind) = edit {
        it.copy(kind = kind, autoRenew = if (kind == RecurringKind.SUBSCRIPTION) true else it.autoRenew)
    }

    fun onAmountChange(text: String) = edit { it.copy(amount = text) }

    fun onEveryChange(text: String) = edit { it.copy(every = text.filter(Char::isDigit).take(3)) }

    fun onUnitClick(unit: CadenceUnit) = edit { it.copy(unit = unit) }

    fun onAutoRenewClick(on: Boolean) = edit { it.copy(autoRenew = on) }

    /** Tapping the chosen lead time again clears it: no reminder. */
    fun onReminderClick(days: Int) = edit { it.copy(reminderDays = days.takeIf { _ -> it.reminderDays != days }) }

    fun onDueClick() {
        val current = form.value ?: return
        sheet.update { EditRecurringSheet.PickDate(ends = false, date = current.nextDue) }
    }

    fun onEndsClick() {
        val current = form.value ?: return
        sheet.update { EditRecurringSheet.PickDate(ends = true, date = current.endsOn ?: current.nextDue.plusYears(1)) }
    }

    fun onEndsClear() = edit { it.copy(endsOn = null) }

    fun onDatePicked(date: LocalDate) {
        val pick = sheet.value as? EditRecurringSheet.PickDate ?: return
        sheet.update { null }
        edit { if (pick.ends) it.copy(endsOn = date) else it.copy(nextDue = date) }
    }

    fun onSheetDismiss() = sheet.update { null }

    fun onDeleteClick() = sheet.update { EditRecurringSheet.ConfirmDelete }

    fun onDeleteConfirm() {
        sheet.update { null }
        viewModelScope.launch {
            domain.delete(id)
            navigator.popBackStack()
        }
    }

    fun onSaveClick() {
        val current = form.value ?: return
        viewModelScope.launch {
            val found = domain.save(id, current)
            problems.update { found }
            if (found.isEmpty()) navigator.popBackStack()
        }
    }

    private fun edit(change: (SeriesForm) -> SeriesForm) {
        form.update { it?.let(change) }
        problems.update { emptySet() }
    }
}
