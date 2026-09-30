package bassamalim.halala.features.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.titleOf
import bassamalim.halala.core.domain.toneOf
import bassamalim.halala.core.enums.AmountTone
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.accountLabel
import bassamalim.halala.core.utils.dateLabel
import bassamalim.halala.core.utils.initialOf
import bassamalim.halala.core.utils.timeLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val domain: TransactionDomain,
    private val navigator: Navigator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val id = savedStateHandle.toRoute<Screen.Transaction>().id

    private val confirmingDelete = MutableStateFlow(false)

    val uiState: StateFlow<TransactionUiState> = combine(
        domain.observe(id),
        confirmingDelete
    ) { detail, confirming ->
        // Gone (deleted from here or elsewhere): the screen stays as it was while it leaves.
        if (detail == null) return@combine TransactionUiState(isLoading = true)

        val tx = detail.transaction
        val zone = domain.zone()
        val today = domain.today()
        val local = tx.occurredAt.atZone(zone)
        val tone = toneOf(detail)
        val here = accountLabel(detail.institutionName, detail.accountNickname)
        val there = detail.counterpartNickname?.let { accountLabel(detail.counterpartInstitutionName, it) }
        val title = titleOf(detail)

        TransactionUiState(
            isLoading = false,
            title = title,
            initial = initialOf(title),
            kind = tx.kind,
            tone = tone,
            amount = Money.format(
                if (tone == AmountTone.Spending) -tx.amountMinor else tx.amountMinor,
                tx.currency,
                showPlus = tone == AmountTone.Income
            ),
            currency = tx.currency,
            whenLabel = "${dateLabel(local.toLocalDate(), today)} · ${timeLabel(local.toLocalTime())}",
            accountLabel = here,
            fromLabel = there?.let { if (detail.isTransferInLeg) it else here },
            toLabel = there?.let { if (detail.isTransferInLeg) here else it },
            note = tx.note,
            source = tx.source,
            createdLabel = dateLabel(tx.createdAt.atZone(zone).toLocalDate(), today),
            isConfirmingDelete = confirming
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TransactionUiState()
    )

    fun onBackClick() = navigator.popBackStack()

    fun onEditClick() = navigator.navigate(Screen.EditTransaction(id = id))

    fun onDeleteClick() = confirmingDelete.update { true }

    fun onDeleteDismiss() = confirmingDelete.update { false }

    fun onDeleteConfirm() {
        confirmingDelete.update { false }
        viewModelScope.launch {
            domain.delete(id)
            navigator.popBackStack()
        }
    }
}
