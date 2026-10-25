package bassamalim.halala.features.goal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import bassamalim.halala.core.domain.toItem
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.features.plan.GoalCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** A savings goal from Plan or Retirement: its card, then each transaction that went into or out of it. */
@HiltViewModel
class GoalViewModel @Inject constructor(
    private val domain: GoalDomain,
    private val navigator: Navigator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val id = savedStateHandle.toRoute<Screen.Goal>().id

    val uiState: StateFlow<GoalUiState> = combine(
        domain.observeStates(),
        domain.observeTransactions(),
        domain.observeDeposits(),
        domain.observeContributions()
    ) { states, details, deposits, contributions ->
        val state = states.firstOrNull { it.goal.id == id } ?: return@combine GoalUiState(isGone = true)
        val zone = domain.zone()
        val today = domain.today()
        GoalUiState(
            isLoading = false,
            name = state.goal.name,
            card = GoalCard.of(state),
            transactions = GoalDomain.transactionsOf(state.goal, details, deposits, contributions).map { it.toItem(zone, today) }
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalUiState())

    fun onBackClick() = navigator.popBackStack()

    fun onEditClick() = navigator.navigate(Screen.EditGoal(id))

    fun onTransactionClick(id: Long) = navigator.navigate(Screen.Transaction(id))
}
