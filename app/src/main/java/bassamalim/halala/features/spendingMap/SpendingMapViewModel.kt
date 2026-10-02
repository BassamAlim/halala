package bassamalim.halala.features.spendingMap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.Globals
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.data.repositories.PlacesRepository
import bassamalim.halala.core.data.repositories.PreferencesRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.Places
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.ui.components.HeatPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.sqrt

enum class MapPeriod { MONTH, THREE_MONTHS, YEAR, ALL }

data class PlaceRow(val key: String, val name: String, val count: Int, val spent: String, val latitude: Double, val longitude: Double)

data class SpendingMapUiState(
    val isLoading: Boolean = true,
    val placesOn: Boolean = false,
    val period: MapPeriod = MapPeriod.THREE_MONTHS,
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val categories: List<Pair<Long, String>> = emptyList(),
    val pickingCategory: Boolean = false,
    val points: List<HeatPoint> = emptyList(),
    val total: String = "",
    val count: Int = 0,
    val top: List<PlaceRow> = emptyList(),
    val focus: Pair<Double, Double>? = null
)

/** Where you spend (the spec's heatmap; no board): the map, its period and category, and the top places. */
@HiltViewModel
class SpendingMapViewModel @Inject constructor(
    transactionsRepository: TransactionsRepository,
    placesRepository: PlacesRepository,
    classificationRepository: ClassificationRepository,
    preferences: PreferencesRepository,
    private val navigator: Navigator,
    private val clock: Clock
) : ViewModel() {

    private data class Filters(val period: MapPeriod = MapPeriod.THREE_MONTHS, val categoryId: Long? = null, val picking: Boolean = false, val focus: String? = null)

    private val filters = MutableStateFlow(Filters())

    val uiState: StateFlow<SpendingMapUiState> = combine(
        transactionsRepository.observeAll(),
        placesRepository.observeAll(),
        classificationRepository.observeCategories(),
        preferences.observePlacesOn(),
        filters
    ) { details, places, categories, on, filters ->
        val today = LocalDate.now(clock)
        val c = Globals.PRIMARY_CURRENCY
        val from = when (filters.period) {
            MapPeriod.MONTH -> today.withDayOfMonth(1)
            MapPeriod.THREE_MONTHS -> today.minusMonths(3)
            MapPeriod.YEAR -> today.minusYears(1)
            MapPeriod.ALL -> LocalDate.MIN
        }
        val spots = Places.spots(details, places, c) {
            !it.transaction.occurredAt.atZone(clock.zone).toLocalDate().isBefore(from) &&
                (filters.categoryId == null || it.transaction.categoryId == filters.categoryId)
        }
        // The heat grows with the square root, so one big purchase doesn't drown the rest.
        val biggest = spots.maxOfOrNull { it.amountMinor }?.coerceAtLeast(1) ?: 1
        val top = Places.top(spots)
        SpendingMapUiState(
            isLoading = false,
            placesOn = on,
            period = filters.period,
            categoryId = filters.categoryId,
            categoryName = categories.firstOrNull { it.id == filters.categoryId }?.name,
            categories = categories.map { it.id to it.name },
            pickingCategory = filters.picking,
            points = spots.map { HeatPoint(it.latitude, it.longitude, sqrt(it.amountMinor.toFloat() / biggest)) },
            total = Money.format(Money.sum(spots.map { it.amountMinor }), c, decimals = false),
            count = spots.size,
            top = top.map { PlaceRow(it.key, it.name, it.count, Money.format(it.spentMinor, c, decimals = false), it.latitude, it.longitude) },
            focus = top.firstOrNull { it.key == filters.focus }?.let { it.latitude to it.longitude }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpendingMapUiState())

    fun onBackClick() = navigator.popBackStack()
    fun onPeriodClick(period: MapPeriod) = filters.update { it.copy(period = period, focus = null) }
    fun onCategoryClick() = filters.update { it.copy(picking = true) }
    fun onCategoryDismiss() = filters.update { it.copy(picking = false) }
    fun onCategoryPicked(id: Long?) = filters.update { it.copy(categoryId = id, picking = false, focus = null) }
    fun onPlaceClick(key: String) = filters.update { it.copy(focus = key) }
    fun onTurnOnClick() = navigator.navigate(Screen.Settings)
}
