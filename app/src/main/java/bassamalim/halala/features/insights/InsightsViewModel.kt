package bassamalim.halala.features.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.Globals
import bassamalim.halala.core.domain.Digests
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.Places
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.places.LocationAccess
import bassamalim.halala.core.ui.components.HeatPoint
import bassamalim.halala.core.utils.monthLabel
import bassamalim.halala.core.utils.monthShortLabel
import bassamalim.halala.core.utils.shortDateLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.YearMonth
import javax.inject.Inject
import kotlin.math.sqrt

/** A month's bar: its share of the tallest month, for drawing. */
data class MonthBar(val month: YearMonth, val label: String, val fraction: Float, val selected: Boolean)

/**
 * A slice of the category chart. [name] is null for unfiled, "" for Other; [categoryId] is null
 * for both.
 */
data class Slice(val categoryId: Long?, val name: String?, val amount: String, val percent: Int, val fraction: Float) {
    val isOther get() = name == ""
}

/** A merchant's row, its bar a share of the biggest. */
data class MerchantBar(val id: Long, val name: String, val amount: String, val fraction: Float)

/** One of the places most was spent at, under the map. */
data class PlaceBar(val name: String, val count: Int, val amount: String)

data class InsightsUiState(
    val isLoading: Boolean = true,
    val monthName: String = "",
    /** The month's spending, summary style. */
    val spent: String = "",
    /** Whole percent against the month before, null when that month had none. */
    val change: Int? = null,
    val previousMonthName: String = "",
    val months: List<MonthBar> = emptyList(),
    /** The months the bars cover ("May – October"), and whether there are older or newer ones to page to. */
    val range: String = "",
    val hasOlder: Boolean = false,
    val hasNewer: Boolean = false,
    val slices: List<Slice> = emptyList(),
    /** The category list under the ring: [slices], or with Other opened, every category. */
    val legend: List<Slice> = emptyList(),
    val otherOpen: Boolean = false,
    val pace: List<Long> = emptyList(),
    val pacePrevious: List<Long> = emptyList(),
    val paceTips: List<Pair<String, String>> = emptyList(),
    val paceLabels: List<String> = emptyList(),
    val merchants: List<MerchantBar> = emptyList(),
    /** Where you spend, the picked month: the map shows only with location allowed all the time. */
    val access: LocationAccess = LocationAccess.GRANTED,
    val points: List<HeatPoint> = emptyList(),
    val placedCount: Int = 0,
    val placedTotal: String = "",
    val places: List<PlaceBar> = emptyList()
)

/** The Insights tab's Spending view: charts of what you spent, for a month you pick on the bars. */
@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val domain: InsightsDomain,
    private val navigator: Navigator
) : ViewModel() {

    /** The month picked on the bars, and the last month the bars show; null is this month. */
    private val picked = MutableStateFlow<YearMonth?>(null)
    private val lastShown = MutableStateFlow<YearMonth?>(null)

    /** Whether Other is opened into the categories it gathers. */
    private val otherOpen = MutableStateFlow(false)

    private val access = MutableStateFlow(domain.locationAccess())

    val uiState: StateFlow<InsightsUiState> = combine(domain.observeLedger(), picked, lastShown, otherOpen, access) { ledger, picked, lastShown, otherOpen, access ->
        val details = ledger.details
        val zone = domain.zone()
        val today = domain.today()
        val currency = Globals.PRIMARY_CURRENCY
        val spending = InsightsDomain.spending(details, currency)
        val month = picked ?: YearMonth.from(today)
        fun f(minor: Long) = Money.format(minor, currency, decimals = false)

        val thisMonth = YearMonth.from(today)
        val last = lastShown ?: thisMonth
        val byMonth = InsightsDomain.byMonth(spending, last, MONTHS, zone)
        val first = byMonth.first().first
        val tallest = byMonth.maxOf { it.second }.coerceAtLeast(1)
        val spent = InsightsDomain.byMonth(spending, month, 2, zone)
        val slices = InsightsDomain.byCategory(spending, month, zone)
        val total = Money.sum(slices.map { it.minor }).coerceAtLeast(1)
        fun slice(it: Share) =
            Slice(it.id, it.name, f(it.minor), Math.multiplyExact(it.minor, 100L).plus(total / 2).div(total).toInt(), it.minor.toFloat() / total)
        val (pace, before) = InsightsDomain.cumulative(spending, month, today, zone)
        val merchants = InsightsDomain.topMerchants(spending, month, zone, MERCHANTS)
        val biggest = merchants.maxOfOrNull { it.minor }?.coerceAtLeast(1) ?: 1
        val spots = InsightsDomain.placed(details, ledger.places, currency, month, zone)
        // The heat grows with the square root, so one big purchase doesn't drown the rest.
        val heaviest = spots.maxOfOrNull { it.amountMinor }?.coerceAtLeast(1) ?: 1

        InsightsUiState(
            isLoading = false,
            monthName = monthLabel(month, today),
            spent = f(-spent.last().second),
            change = Digests.percent(spent.last().second, spent.first().second),
            previousMonthName = monthLabel(month.minusMonths(1), today),
            range = "${monthLabel(first, today)} – ${monthLabel(last, today)}",
            hasOlder = InsightsDomain.firstMonth(spending, zone)?.let { it < first } == true,
            hasNewer = last < thisMonth,
            months = byMonth.map { (m, minor) -> MonthBar(m, monthShortLabel(m), minor.toFloat() / tallest, m == month) },
            slices = slices.map(::slice),
            legend = (if (otherOpen) InsightsDomain.categories(spending, month, zone) else slices).map(::slice),
            otherOpen = otherOpen,
            pace = pace,
            pacePrevious = before,
            paceTips = pace.mapIndexed { i, v -> shortDateLabel(month.atDay(i + 1), today) to f(v) },
            paceLabels = listOf(shortDateLabel(month.atDay(1), today), shortDateLabel(month.atDay(pace.size), today)),
            merchants = merchants.map { MerchantBar(it.id!!, it.name.orEmpty(), f(it.minor), it.minor.toFloat() / biggest) },
            access = access,
            points = spots.map { HeatPoint(it.latitude, it.longitude, sqrt(it.amountMinor.toFloat() / heaviest)) },
            placedCount = spots.size,
            placedTotal = f(Money.sum(spots.map { it.amountMinor })),
            places = Places.top(spots, PLACES).map { PlaceBar(it.name, it.count, f(it.spentMinor)) }
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    fun onMonthClick(month: YearMonth) {
        picked.value = month
    }

    /** Six months back (or forward, never past this one); the page follows the newest shown. */
    fun onOlderClick() = page(-MONTHS.toLong())

    fun onNewerClick() = page(MONTHS.toLong())

    private fun page(by: Long) {
        val thisMonth = YearMonth.from(domain.today())
        val last = minOf((lastShown.value ?: thisMonth).plusMonths(by), thisMonth)
        lastShown.value = last.takeIf { it != thisMonth }
        picked.value = last.takeIf { it != thisMonth }
    }

    fun onMerchantClick(id: Long) = navigator.navigate(Screen.Merchant(id))

    /** A category opens what it was made of that month; Other opens into its categories, and closes again. */
    fun onSliceClick(slice: Slice) {
        if (slice.isOther) return otherOpen.update { !it }
        val month = picked.value ?: YearMonth.from(domain.today())
        navigator.navigate(Screen.CategorySpending(slice.categoryId ?: 0, month.toString()))
    }

    fun onOtherCloseClick() = otherOpen.update { false }

    /** The whole map, to look around, over other periods and categories. */
    fun onMapClick() = navigator.navigate(Screen.SpendingMap)

    /** Coming back from Android's settings or a permission dialog: look again. */
    fun onCheckAccess() = access.update { domain.locationAccess() }

    private companion object {
        const val MONTHS = 6
        const val MERCHANTS = 5
        const val PLACES = 3
    }
}
