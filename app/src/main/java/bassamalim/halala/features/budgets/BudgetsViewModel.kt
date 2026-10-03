package bassamalim.halala.features.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.Globals
import bassamalim.halala.core.data.repositories.BudgetsRepository
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.shortDateLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    budgetsRepository: BudgetsRepository,
    classificationRepository: ClassificationRepository,
    private val navigator: Navigator,
    private val clock: Clock
) : ViewModel() {

    val uiState: StateFlow<BudgetsUiState> = combine(
        budgetsRepository.observeOverview(Globals.PRIMARY_CURRENCY),
        classificationRepository.observeCategories(),
        classificationRepository.observeAllMerchants()
    ) { overview, categories, merchants ->
        val today = LocalDate.now(clock)
        BudgetsUiState(
            isLoading = false,
            cycle = cycleLabel(overview.cycle.start, overview.cycle.end, today),
            rows = BudgetRows.of(overview.statuses, categories, merchants, overview.tagNames)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState())

    fun onBackClick() = navigator.popBackStack()

    fun onAddClick() = navigator.navigate(Screen.EditBudget())

    fun onBudgetClick(id: Long) = navigator.navigate(Screen.EditBudget(id))

    companion object {
        /** The cycle as the Plan board's chip writes it, its last day included: "27 Sep – 26 Oct". */
        fun cycleLabel(start: LocalDate, end: LocalDate, today: LocalDate) =
            "${shortDateLabel(start, today)} – ${shortDateLabel(end.minusDays(1), today)}"
    }
}
