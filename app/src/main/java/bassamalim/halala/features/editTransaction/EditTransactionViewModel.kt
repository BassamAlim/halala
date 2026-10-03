package bassamalim.halala.features.editTransaction

import bassamalim.halala.core.utils.OneAtATime
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.accountLabel
import bassamalim.halala.core.utils.dateLabel
import bassamalim.halala.core.utils.timeLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class EditTransactionViewModel @Inject constructor(
    private val domain: EditTransactionDomain,
    private val navigator: Navigator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val saving = OneAtATime()

    private val route = savedStateHandle.toRoute<Screen.EditTransaction>()
    private val id = route.id

    private val local = MutableStateFlow(
        Local(
            form = domain.now().let { now ->
                TransactionForm(date = now.toLocalDate(), time = now.toLocalTime().withSecond(0).withNano(0))
            }
        )
    )

    val uiState: StateFlow<EditTransactionUiState> = combine(
        domain.observeAccounts(),
        local
    ) { accounts, local ->
        val form = local.form
        val today = domain.now().toLocalDate()
        // Archived accounts drop out of the choice, unless this transaction is already on one.
        val options = accounts
            .filter { (!it.account.archived && it.account.type.listed) || it.account.id == form.accountId || it.account.id == form.toAccountId }
            .map {
                AccountOption(
                    id = it.account.id,
                    label = accountLabel(it.institutionName, it.account.nickname),
                    currency = it.account.currency,
                    isCash = EditTransactionDomain.isCash(it)
                )
            }

        EditTransactionUiState(
            isLoading = local.isLoading,
            isNew = id == 0L,
            modes = when {
                id == 0L -> EntryMode.entries
                form.mode == EntryMode.MOVE -> listOf(EntryMode.MOVE)
                else -> listOf(EntryMode.OUT, EntryMode.IN)
            },
            form = form,
            accounts = options,
            kinds = EditTransactionDomain.kindsFor(form.mode),
            currency = options.firstOrNull { it.id == form.accountId }?.currency.orEmpty(),
            dateLabel = dateLabel(form.date, today),
            timeLabel = timeLabel(form.time),
            isPickingDate = local.isPickingDate,
            isPickingTime = local.isPickingTime,
            problems = local.problems
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EditTransactionUiState()
    )

    init {
        viewModelScope.launch {
            if (id != 0L) {
                val form = domain.load(id) ?: return@launch navigator.popBackStack()
                local.update { it.copy(isLoading = false, form = form) }
            }
            else {
                // Quick-add lands on the account it was opened from, else the wallet.
                val accountId = route.accountId.takeIf { it != 0L } ?: domain.cashWalletId()
                local.update { it.copy(isLoading = false, form = it.form.copy(accountId = accountId)) }
            }
        }
    }

    fun onBackClick() = navigator.popBackStack()

    fun onModeClick(mode: EntryMode) = edit {
        it.copy(
            mode = mode,
            kind = EditTransactionDomain.kindsFor(mode).firstOrNull() ?: TransactionKind.INTERNAL_TRANSFER
        )
    }

    fun onAmountChange(amount: String) = edit { it.copy(amount = amount) }

    fun onAccountClick(accountId: Long) = edit { it.copy(accountId = accountId) }

    fun onToAccountClick(accountId: Long) = edit { it.copy(toAccountId = accountId) }

    fun onKindClick(kind: TransactionKind) = edit { it.copy(kind = kind) }

    fun onTitleChange(title: String) = edit { it.copy(title = title) }

    fun onNoteChange(note: String) = edit { it.copy(note = note) }

    fun onDateClick() = local.update { it.copy(isPickingDate = true) }

    fun onTimeClick() = local.update { it.copy(isPickingTime = true) }

    fun onPickerDismiss() = local.update { it.copy(isPickingDate = false, isPickingTime = false) }

    fun onDatePicked(date: LocalDate) =
        local.update { it.copy(isPickingDate = false, form = it.form.copy(date = date)) }

    fun onTimePicked(time: LocalTime) =
        local.update { it.copy(isPickingTime = false, form = it.form.copy(time = time)) }

    fun onSaveClick() {
        val state = uiState.value
        if (state.isLoading) return

        saving.launch(viewModelScope) {
            val problems = domain.save(id, local.value.form, state.accounts)

            if (problems.isEmpty()) navigator.popBackStack()
            else local.update { it.copy(problems = problems) }
            problems.isEmpty()
        }
    }

    /** An edit clears the problems it might have fixed; Save checks again. */
    private fun edit(change: (TransactionForm) -> TransactionForm) =
        local.update { it.copy(form = change(it.form), problems = emptySet()) }

    private data class Local(
        val isLoading: Boolean = true,
        val form: TransactionForm,
        val isPickingDate: Boolean = false,
        val isPickingTime: Boolean = false,
        val problems: Set<TransactionProblem> = emptySet()
    )
}
