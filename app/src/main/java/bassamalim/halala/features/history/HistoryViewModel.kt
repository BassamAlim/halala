package bassamalim.halala.features.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.utils.dayLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val domain: HistoryDomain,
    private val navigator: Navigator
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = domain.observeBatches().map { batches ->
        val zone = domain.zone()
        val today = domain.today()

        HistoryUiState(
            isLoading = false,
            changes = batches.map { (batch, transactions) ->
                HistoryItem(
                    id = batch.id,
                    action = batch.action,
                    subject = batch.subject,
                    detail = batch.detail,
                    transactions = transactions,
                    day = dayLabel(batch.at.atZone(zone).toLocalDate(), today),
                    undone = batch.undoneAt != null
                )
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState()
    )

    fun onBackClick() = navigator.popBackStack()

    fun onUndoClick(id: Long) {
        viewModelScope.launch { domain.undo(id) }
    }
}
