package bassamalim.halala.features.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.Globals
import bassamalim.halala.core.domain.Digests
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.monthLabel
import bassamalim.halala.core.utils.monthShortLabel
import bassamalim.halala.core.utils.shortDateLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import javax.inject.Inject

/** A month's bar: its share of the tallest month, for drawing. */
data class MonthBar(val month: YearMonth, val label: String, val fraction: Float, val selected: Boolean)

/** A slice of the category chart. [name] is null for unfiled, "" for Other. */
data class Slice(val name: String?, val amount: String, val percent: Int, val fraction: Float)

/** A merchant's row, its bar a share of the biggest. */
data class MerchantBar(val id: Long, val name: String, val amount: String, val fraction: Float)

data class InsightsUiState(
    val isLoading: Boolean = true,
    val monthName: String = "",
    /** The month's spending, summary style. */
    val spent: String = "",
    /** Whole percent against the month before, null when that month had none. */
    val change: Int? = null,
    val previousMonthName: String = "",
    val months: List<MonthBar> = emptyList(),
    val slices: List<Slice> = emptyList(),
    val pace: List<Long> = emptyList(),
    val pacePrevious: List<Long> = emptyList(),
    val paceTips: List<Pair<String, String>> = emptyList(),
    val paceLabels: List<String> = emptyList(),
    val merchants: List<MerchantBar> = emptyList()
)

/** Activity's third segment: charts of what you spent, for a month you pick on the bars. */
@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val domain: InsightsDomain,
    private val navigator: Navigator
) : ViewModel() {

    /** The month picked on the bars; null is this month. */
    private val picked = MutableStateFlow<YearMonth?>(null)

    val uiState: StateFlow<InsightsUiState> = combine(domain.observeTransactions(), picked) { details, picked ->
        val zone = domain.zone()
        val today = domain.today()
        val currency = Globals.PRIMARY_CURRENCY
        val spending = InsightsDomain.spending(details, currency)
        val month = picked ?: YearMonth.from(today)
        fun f(minor: Long) = Money.format(minor, currency, decimals = false)

        val byMonth = InsightsDomain.byMonth(spending, YearMonth.from(today), MONTHS, zone)
        val tallest = byMonth.maxOf { it.second }.coerceAtLeast(1)
        val spent = InsightsDomain.byMonth(spending, month, 2, zone)
        val slices = InsightsDomain.byCategory(spending, month, zone)
        val total = Money.sum(slices.map { it.minor }).coerceAtLeast(1)
        val (pace, before) = InsightsDomain.cumulative(spending, month, today, zone)
        val merchants = InsightsDomain.topMerchants(spending, month, zone, MERCHANTS)
        val biggest = merchants.maxOfOrNull { it.minor }?.coerceAtLeast(1) ?: 1

        InsightsUiState(
            isLoading = false,
            monthName = monthLabel(month, today),
            spent = f(-spent.last().second),
            change = Digests.percent(spent.last().second, spent.first().second),
            previousMonthName = monthLabel(month.minusMonths(1), today),
            months = byMonth.map { (m, minor) -> MonthBar(m, monthShortLabel(m), minor.toFloat() / tallest, m == month) },
            slices = slices.map {
                Slice(it.name, f(it.minor), Math.multiplyExact(it.minor, 100L).plus(total / 2).div(total).toInt(), it.minor.toFloat() / total)
            },
            pace = pace,
            pacePrevious = before,
            paceTips = pace.mapIndexed { i, v -> shortDateLabel(month.atDay(i + 1), today) to f(v) },
            paceLabels = listOf(shortDateLabel(month.atDay(1), today), shortDateLabel(month.atDay(pace.size), today)),
            merchants = merchants.map { MerchantBar(it.id!!, it.name.orEmpty(), f(it.minor), it.minor.toFloat() / biggest) }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    fun onMonthClick(month: YearMonth) {
        picked.value = month
    }

    fun onMerchantClick(id: Long) = navigator.navigate(Screen.Merchant(id))

    private companion object {
        const val MONTHS = 6
        const val MERCHANTS = 5
    }
}
