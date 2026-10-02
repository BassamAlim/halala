package bassamalim.halala.features.forecast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.Globals
import bassamalim.halala.core.domain.Forecasts
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.enums.BudgetScope
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.utils.shortDateLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class ForecastViewModel @Inject constructor(
    private val domain: ForecastDomain,
    private val navigator: Navigator
) : ViewModel() {

    private val afford = MutableStateFlow(AffordForm())

    val uiState: StateFlow<ForecastUiState> = combine(
        domain.observeInputs(),
        domain.observeAccounts(),
        domain.observeTransactions(),
        domain.observeOverview(),
        afford
    ) { inputs, accounts, details, overview, form ->
        val today = inputs.today
        val currency = Globals.PRIMARY_CURRENCY
        val estimate = Forecasts.endOfCycle(inputs)
        val cycleLast = inputs.cycle.end.minusDays(1)
        val future = Forecasts.path(inputs, cycleLast).map { it.second }
        val months = Forecasts.monthsAhead(inputs, MONTHS)
        val largest = months.maxOfOrNull { abs(it.leftMinor) }?.takeIf { it > 0 } ?: 1
        val lowest = months.minByOrNull { it.leftMinor }
        val typical = months.map { it.leftMinor }.sorted().let { it[it.size / 2] }

        // The answer, recomputed as the forecast moves.
        val result = form.on?.let { on ->
            Money.parse(form.amount, currency)?.takeIf { it > 0 }?.let { amount ->
                val answer = Forecasts.afford(inputs, amount, on)
                val total = overview.statuses.firstOrNull { it.budget.scope == BudgetScope.TOTAL }
                AffordResult(
                    affordable = answer.affordable,
                    lowest = Money.format(answer.lowestMinor, currency, decimals = false),
                    on = shortDateLabel(answer.lowestOn, today),
                    overBudget = total != null && inputs.cycle.contains(on) && amount > total.leftMinor
                )
            }
        }

        ForecastUiState(
            isLoading = false,
            end = estimate?.let { Money.format(it.midMinor, currency, decimals = false) },
            low = estimate?.let { Money.format(it.lowMinor, currency, decimals = false) }.orEmpty(),
            high = estimate?.let { Money.format(it.highMinor, currency, decimals = false) }.orEmpty(),
            salaryFrom = inputs.cycle.takeIf { it.fromSalary && inputs.salaryMinor != null }?.end?.minusDays(1)?.dayOfMonth?.toString(),
            salaryTo = inputs.cycle.takeIf { it.fromSalary && inputs.salaryMinor != null }?.end?.plusDays(1)?.let { shortDateLabel(it, today) },
            chart = estimate?.let {
                ForecastChart(
                    past = ForecastDomain.history(inputs.balanceMinor, inputs.cycle.start, today, accounts, details, domain.zone()),
                    future = future,
                    endLow = it.lowMinor,
                    endHigh = it.highMinor,
                    startLabel = shortDateLabel(inputs.cycle.start, today),
                    endLabel = shortDateLabel(cycleLast, today)
                )
            },
            months = months.map {
                MonthBar(
                    label = it.month.atDay(1).format(MONTH),
                    left = Money.format(it.leftMinor, currency, decimals = false, showPlus = it.leftMinor > 0),
                    negative = it.leftMinor < 0,
                    fraction = (abs(it.leftMinor).toDouble() / largest).toFloat()
                )
            },
            dip = lowest?.takeIf { it.leftMinor < typical && it.biggest.isNotEmpty() }?.let {
                Dip(
                    month = it.month.atDay(1).format(MONTH_LONG),
                    items = it.biggest.map { item -> item.name to Money.format(item.amountMinor, currency, decimals = false) }
                )
            },
            afford = form.copy(result = result, onLabel = form.on?.let { shortDateLabel(it, today) }.orEmpty(), pickFrom = form.on ?: today)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ForecastUiState())

    fun onBackClick() = navigator.popBackStack()

    fun onAmountChange(text: String) = afford.update { it.copy(amount = text) }

    fun onDateClick() = afford.update { it.copy(picking = true) }

    fun onDatePicked(date: LocalDate) = afford.update { it.copy(on = date, picking = false) }

    fun onDateDismiss() = afford.update { it.copy(picking = false) }

    private companion object {
        const val MONTHS = 6
        val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.US)
        val MONTH_LONG: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM", Locale.US)
    }
}
