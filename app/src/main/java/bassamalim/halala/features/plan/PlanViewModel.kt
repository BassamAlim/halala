package bassamalim.halala.features.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.Globals
import bassamalim.halala.core.data.repositories.BudgetsRepository
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.data.repositories.ForecastRepository
import bassamalim.halala.core.domain.Forecasts
import bassamalim.halala.core.data.repositories.RecurringRepository
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.shortDateLabel
import bassamalim.halala.features.budgets.BudgetRow
import bassamalim.halala.features.budgets.BudgetRows
import bassamalim.halala.features.budgets.BudgetsViewModel
import bassamalim.halala.features.recurring.RecurringDomain
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class PlanUiState(
    val isLoading: Boolean = true,
    /** "27 Sep – 26 Oct": this pay cycle. */
    val cycle: String = "",
    val budgets: List<BudgetRow> = emptyList(),
    /** What subscriptions and bills cost a month, summary style. */
    val monthly: String = "",
    /** The next one due: its name and day ("Netflix", "3 Oct"). */
    val next: Pair<String, String>? = null,
    /** The forecast end of this cycle, summary style; null until there is one. */
    val endAbout: String? = null
)

@HiltViewModel
class PlanViewModel @Inject constructor(
    recurringRepository: RecurringRepository,
    budgetsRepository: BudgetsRepository,
    classificationRepository: ClassificationRepository,
    forecastRepository: ForecastRepository,
    private val navigator: Navigator,
    private val clock: Clock
) : ViewModel() {

    val uiState: StateFlow<PlanUiState> = combine(
        recurringRepository.observeStates(),
        budgetsRepository.observeOverview(Globals.PRIMARY_CURRENCY),
        classificationRepository.observeCategories(),
        classificationRepository.observeAllMerchants(),
        forecastRepository.observeInputs(Globals.PRIMARY_CURRENCY)
    ) { states, overview, categories, merchants, inputs ->
        val today = LocalDate.now(clock)
        val currency = Globals.PRIMARY_CURRENCY
        PlanUiState(
            isLoading = false,
            cycle = BudgetsViewModel.cycleLabel(overview.cycle.start, overview.cycle.end, today),
            budgets = BudgetRows.of(overview.statuses, categories, merchants),
            monthly = Money.format(RecurringDomain.totals(states, currency).first, currency, decimals = false),
            next = RecurringDomain.upcoming(states).firstOrNull()?.let { it.series.name to shortDateLabel(it.nextDue!!, today) },
            endAbout = Forecasts.endOfCycle(inputs)?.let { Money.format(it.midMinor, currency, decimals = false) }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlanUiState()
    )

    fun onRecurringClick() = navigator.navigate(Screen.Recurring)

    fun onBudgetsClick() = navigator.navigate(Screen.Budgets)

    fun onForecastClick() = navigator.navigate(Screen.Forecast)

    fun onAddBudgetClick() = navigator.navigate(Screen.EditBudget())
}
