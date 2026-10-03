package bassamalim.halala.features.categorySpending

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import bassamalim.halala.core.Globals
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.toItem
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.monthLabel
import bassamalim.halala.features.insights.InsightsDomain
import bassamalim.halala.features.insights.MerchantBar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import javax.inject.Inject

/** Insights' drill-down: one category in one month, by merchant and transaction by transaction. */
@HiltViewModel
class CategorySpendingViewModel @Inject constructor(
    private val domain: CategorySpendingDomain,
    private val navigator: Navigator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Screen.CategorySpending>()
    private val categoryId = route.categoryId.takeIf { it != 0L }
    private val month = YearMonth.parse(route.month)

    val uiState: StateFlow<CategorySpendingUiState> = combine(
        domain.observeTransactions(),
        domain.observeCategories()
    ) { details, categories ->
        val zone = domain.zone()
        val today = domain.today()
        val currency = Globals.PRIMARY_CURRENCY
        val mine = CategorySpendingDomain.of(details, categoryId, month, currency, zone)
        val merchants = InsightsDomain.topMerchants(mine, month, zone, Int.MAX_VALUE)
        val biggest = merchants.maxOfOrNull { it.minor }?.coerceAtLeast(1) ?: 1

        CategorySpendingUiState(
            isLoading = false,
            name = categoryId?.let { id -> categories.firstOrNull { it.id == id }?.name.orEmpty() },
            monthName = monthLabel(month, today),
            total = Money.format(Money.sum(mine.map { it.yourMinor }), currency, decimals = false),
            currency = currency,
            count = mine.size,
            merchants = merchants.map {
                MerchantBar(it.id!!, it.name.orEmpty(), Money.format(it.minor, currency, decimals = false), it.minor.toFloat() / biggest)
            },
            transactions = mine.map { it.toItem(zone, today) }
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategorySpendingUiState())

    fun onBackClick() = navigator.popBackStack()

    fun onMerchantClick(id: Long) = navigator.navigate(Screen.Merchant(id))

    fun onTransactionClick(id: Long) = navigator.navigate(Screen.Transaction(id))
}
