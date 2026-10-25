package bassamalim.halala.features.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.Globals
import bassamalim.halala.core.domain.Budgets
import bassamalim.halala.core.domain.Digests
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.Places
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.places.LocationAccess
import bassamalim.halala.core.ui.components.HeatPoint
import bassamalim.halala.core.utils.cycleLabel
import bassamalim.halala.core.utils.cycleShortLabel
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
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.sqrt

/** A pay cycle's bar, by the day it began: its share of the tallest, for drawing. */
data class MonthBar(val start: LocalDate, val label: String, val fraction: Float, val selected: Boolean)

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
    /** The pay cycle picked: "27 Sep – 26 Oct". */
    val monthName: String = "",
    /** The cycle's spending, summary style. */
    val spent: String = "",
    /** Whole percent against the cycle before, null when that cycle had none. */
    val change: Int? = null,
    val months: List<MonthBar> = emptyList(),
    /** The days the bars cover ("27 May – 26 Oct"), and whether there are older or newer cycles to page to. */
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
    /** Where you spend, the picked cycle: the map shows only with location allowed all the time. */
    val access: LocationAccess = LocationAccess.GRANTED,
    val points: List<HeatPoint> = emptyList(),
    val placedCount: Int = 0,
    val placedTotal: String = "",
    val places: List<PlaceBar> = emptyList()
)

/** The Insights tab's Spending view: charts of what you spent, for a pay cycle you pick on the bars. */
@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val domain: InsightsDomain,
    private val navigator: Navigator
) : ViewModel() {

    /** A day in the cycle picked on the bars (null: the newest shown), and how many pages of six back the bars are. */
    private val picked = MutableStateFlow<LocalDate?>(null)
    private val page = MutableStateFlow(0)

    /** Whether Other is opened into the categories it gathers. */
    private val otherOpen = MutableStateFlow(false)

    private val access = MutableStateFlow(domain.locationAccess())

    val uiState: StateFlow<InsightsUiState> = combine(domain.observeLedger(), picked, page, otherOpen, access) { ledger, picked, page, otherOpen, access ->
        val details = ledger.details
        val zone = domain.zone()
        val today = domain.today()
        val currency = Globals.PRIMARY_CURRENCY
        val spending = InsightsDomain.spending(details, currency)
        val cycles = Budgets.cycles(details, zone, today)
        val last = cycles.back(cycles.current, page * CYCLES + 1).last()
        val cycle = picked?.let(cycles::of) ?: last
        val before = cycles.before(cycle)
        fun f(minor: Long) = Money.format(minor, currency, decimals = false)

        val byCycle = InsightsDomain.byCycle(spending, cycles.back(last, CYCLES).reversed(), zone)
        val first = byCycle.first().first
        val tallest = byCycle.maxOf { it.second }.coerceAtLeast(1)
        val spent = InsightsDomain.byCycle(spending, listOf(before, cycle), zone)
        val slices = InsightsDomain.byCategory(spending, cycle, zone)
        val total = Money.sum(slices.map { it.minor }).coerceAtLeast(1)
        fun slice(it: Share) =
            Slice(it.id, it.name, f(it.minor), Math.multiplyExact(it.minor, 100L).plus(total / 2).div(total).toInt(), it.minor.toFloat() / total)
        val (pace, pacePrevious) = InsightsDomain.cumulative(spending, cycle, before, today, zone)
        val merchants = InsightsDomain.topMerchants(spending, cycle, zone, MERCHANTS)
        val biggest = merchants.maxOfOrNull { it.minor }?.coerceAtLeast(1) ?: 1
        val spots = InsightsDomain.placed(details, ledger.places, currency, cycle, zone)
        // The heat grows with the square root, so one big purchase doesn't drown the rest.
        val heaviest = spots.maxOfOrNull { it.amountMinor }?.coerceAtLeast(1) ?: 1

        InsightsUiState(
            isLoading = false,
            monthName = cycleLabel(cycle, today),
            spent = f(-spent.last().second),
            change = Digests.percent(spent.last().second, spent.first().second),
            range = "${shortDateLabel(first.start, today)} – ${shortDateLabel(last.end.minusDays(1), today)}",
            hasOlder = InsightsDomain.firstDay(spending, zone)?.let { it < first.start } == true,
            hasNewer = page > 0,
            months = byCycle.map { (c, minor) -> MonthBar(c.start, cycleShortLabel(c), minor.toFloat() / tallest, c == cycle) },
            slices = slices.map(::slice),
            legend = (if (otherOpen) InsightsDomain.categories(spending, cycle, zone) else slices).map(::slice),
            otherOpen = otherOpen,
            pace = pace,
            pacePrevious = pacePrevious,
            paceTips = pace.mapIndexed { i, v -> shortDateLabel(cycle.start.plusDays(i.toLong()), today) to f(v) },
            paceLabels = listOf(
                shortDateLabel(cycle.start, today),
                shortDateLabel(cycle.start.plusDays(pace.size - 1L), today)
            ),
            merchants = merchants.map { MerchantBar(it.id!!, it.name.orEmpty(), f(it.minor), it.minor.toFloat() / biggest) },
            access = access,
            points = spots.map { HeatPoint(it.latitude, it.longitude, sqrt(it.amountMinor.toFloat() / heaviest)) },
            placedCount = spots.size,
            placedTotal = f(Money.sum(spots.map { it.amountMinor })),
            places = Places.top(spots, PLACES).map { PlaceBar(it.name, it.count, f(it.spentMinor)) }
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    /** A bar, by the day its cycle began. */
    fun onMonthClick(start: LocalDate) {
        picked.value = start
    }

    /** Six cycles back (or forward, never past this one); the page follows the newest shown. */
    fun onOlderClick() = turn(1)

    fun onNewerClick() = turn(-1)

    private fun turn(by: Int) {
        page.update { (it + by).coerceAtLeast(0) }
        picked.value = null
    }

    fun onMerchantClick(id: Long) = navigator.navigate(Screen.Merchant(id))

    /** A category opens what it was made of that month; Other opens into its categories, and closes again. */
    fun onSliceClick(slice: Slice) {
        if (slice.isOther) return otherOpen.update { !it }
        val start = uiState.value.months.firstOrNull { it.selected }?.start ?: return
        navigator.navigate(Screen.CategorySpending(slice.categoryId ?: 0, start.toString()))
    }

    fun onOtherCloseClick() = otherOpen.update { false }

    /** The whole map, to look around, over other periods and categories. */
    fun onMapClick() = navigator.navigate(Screen.SpendingMap)

    /** Coming back from Android's settings or a permission dialog: look again. */
    fun onCheckAccess() = access.update { domain.locationAccess() }

    private companion object {
        const val CYCLES = 6
        const val MERCHANTS = 5
        const val PLACES = 3
    }
}
