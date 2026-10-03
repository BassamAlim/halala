package bassamalim.halala.features.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.Globals
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.inOut
import bassamalim.halala.core.domain.toItem
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.accountLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val domain: ActivityDomain,
    private val navigator: Navigator
) : ViewModel() {

    /** What this screen owns rather than the database: the search and the account filter. */
    private val filters = MutableStateFlow(Filters())

    /**
     * The search echoes from [filters] on the main thread, so typing never waits; what it finds
     * is worked out over the whole ledger in the background and follows.
     */
    val uiState: StateFlow<ActivityUiState> = combine(
        domain.observeAccounts(),
        domain.observeTransactions(),
        filters
    ) { accounts, transactions, filters ->
        val zone = domain.zone()
        val today = domain.today()
        val shown = ActivityDomain.filter(transactions, filters.accountId, filters.query)
        val month = inOut(ActivityDomain.thisMonth(shown, zone, today), Globals.PRIMARY_CURRENCY)

        ActivityUiState(
            isLoading = false,
            query = filters.query,
            selectedAccountId = filters.accountId,
            accountFilters = accounts
                .filter { !it.account.archived && it.account.type.listed }
                .map { AccountFilter(it.account.id, accountLabel(it.institutionName, it.account.nickname)) },
            monthIn = Money.format(month.inMinor, Globals.PRIMARY_CURRENCY, decimals = false, showPlus = true),
            monthOut = Money.format(-month.outMinor, Globals.PRIMARY_CURRENCY, decimals = false),
            groups = shown
                .map { it.toItem(zone, today) }
                .groupBy { it.date }
                .map { (_, items) -> DayGroup(items.first().day, items) },
            hasAny = transactions.isNotEmpty()
        )
    }.flowOn(Dispatchers.Default).combine(filters) { state, filters ->
        state.copy(query = filters.query, selectedAccountId = filters.accountId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ActivityUiState()
    )

    fun onQueryChange(query: String) = filters.update { it.copy(query = query) }

    /** Tapping the selected account again goes back to all of them. */
    fun onAccountFilterClick(accountId: Long?) = filters.update {
        it.copy(accountId = if (accountId == it.accountId) null else accountId)
    }

    fun onTransactionClick(id: Long) = navigator.navigate(Screen.Transaction(id))

    fun onMapClick() = navigator.navigate(Screen.SpendingMap)

    fun onMerchantsClick() = navigator.navigate(Screen.Merchants)

    fun onPeopleClick() = navigator.navigate(Screen.People)

    fun onTagsClick() = navigator.navigate(Screen.Tags)

    fun onDigestsClick() = navigator.navigate(Screen.Digests)

    private data class Filters(val query: String = "", val accountId: Long? = null)
}
