package bassamalim.halala.features.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class InboxRow(val kind: InboxKind, val count: Int)

data class InboxUiState(val isLoading: Boolean = true, val rows: List<InboxRow> = emptyList())

@HiltViewModel
class InboxViewModel @Inject constructor(
    domain: InboxDomain,
    private val navigator: Navigator
) : ViewModel() {

    val uiState: StateFlow<InboxUiState> = domain.observeCounts()
        .map { counts -> InboxUiState(isLoading = false, rows = InboxKind.entries.mapNotNull { kind -> counts[kind]?.let { InboxRow(kind, it) } }) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxUiState())

    fun onBackClick() = navigator.popBackStack()

    fun onRowClick(kind: InboxKind) = navigator.navigate(
        when (kind) {
            InboxKind.MERCHANTS -> Screen.Review
            InboxKind.SAME_MERCHANT -> Screen.Merchants
            InboxKind.ALERTS -> Screen.Alerts
            InboxKind.RECURRING -> Screen.Recurring
            InboxKind.PEOPLE -> Screen.People
            InboxKind.TRIPS -> Screen.Tags
        }
    )
}
