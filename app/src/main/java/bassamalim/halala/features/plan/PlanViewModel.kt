package bassamalim.halala.features.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.Globals
import bassamalim.halala.core.data.repositories.RecurringRepository
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.shortDateLabel
import bassamalim.halala.features.recurring.RecurringDomain
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class PlanUiState(
    /** What subscriptions and bills cost a month, summary style. */
    val monthly: String = "",
    /** The next one due: its name and day ("Netflix", "3 Oct"). */
    val next: Pair<String, String>? = null
)

@HiltViewModel
class PlanViewModel @Inject constructor(
    recurringRepository: RecurringRepository,
    private val navigator: Navigator,
    private val clock: Clock
) : ViewModel() {

    val uiState: StateFlow<PlanUiState> = recurringRepository.observeStates().map { states ->
        val today = LocalDate.now(clock)
        val currency = Globals.PRIMARY_CURRENCY
        PlanUiState(
            monthly = Money.format(RecurringDomain.totals(states, currency).first, currency, decimals = false),
            next = RecurringDomain.upcoming(states).firstOrNull()?.let { it.series.name to shortDateLabel(it.nextDue!!, today) }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlanUiState()
    )

    fun onRecurringClick() = navigator.navigate(Screen.Recurring)
}
