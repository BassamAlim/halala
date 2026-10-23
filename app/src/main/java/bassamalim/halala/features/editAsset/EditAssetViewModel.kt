package bassamalim.halala.features.editAsset

import bassamalim.halala.core.utils.OneAtATime
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import bassamalim.halala.core.enums.AssetType
import bassamalim.halala.core.Globals
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.titleOf
import bassamalim.halala.core.prices.ListedFund
import bassamalim.halala.core.prices.PriceProtocol
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.shortDateLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class EditAssetUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val form: AssetForm = AssetForm(),
    /** "28 Sep": when its price or value is from; today until you say. */
    val dateLabel: String = "",
    val problems: Set<AssetProblem> = emptySet(),
    val pickingDate: Boolean = false,
    val pickFrom: LocalDate = LocalDate.MIN,
    val isConfirmingDelete: Boolean = false,
    /** Linking a fund: what you typed, the funds it finds (null while loading, empty when none). */
    val fundSearch: FundSearch? = null,
    /** Fetching today's gold price failed (offline). */
    val goldFailed: Boolean = false,
    /** The transactions you said bought it; once there are any, what it cost is their total ([paid]). */
    val purchases: List<PurchaseRow> = emptyList(),
    val paid: String? = null
)

data class PurchaseRow(val transactionId: Long, val title: String, val date: String, val amount: String)

data class FundSearch(val query: String = "", val results: List<FundOption>? = null, val failed: Boolean = false)

data class FundOption(val id: Long, val name: String, val manager: String?, val price: String, val date: String?)

@HiltViewModel
class EditAssetViewModel @Inject constructor(
    private val domain: EditAssetDomain,
    private val navigator: Navigator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val saving = OneAtATime()

    private val id = savedStateHandle.toRoute<Screen.EditAsset>().id

    private val form = MutableStateFlow(if (id == 0L) AssetForm() else null)
    private val problems = MutableStateFlow(emptySet<AssetProblem>())
    private val picking = MutableStateFlow(false)
    private val confirmingDelete = MutableStateFlow(false)
    private val search = MutableStateFlow<FundSearch?>(null)
    private val goldFailed = MutableStateFlow(false)
    private var funds: List<ListedFund>? = null

    init {
        if (id != 0L) viewModelScope.launch { form.value = domain.load(id) ?: AssetForm() }
    }

    val uiState: StateFlow<EditAssetUiState> = combine(
        form, problems, picking, confirmingDelete, combine(search, goldFailed, domain.observePurchases(id), ::Triple)
    ) { form, problems, picking, confirming, (search, goldFailed, purchases) ->
        val today = domain.today()
        val currency = form?.currency ?: Globals.PRIMARY_CURRENCY
        EditAssetUiState(
            isLoading = form == null,
            isNew = id == 0L,
            form = form ?: AssetForm(),
            dateLabel = shortDateLabel(form?.priceDate ?: today, today),
            problems = problems,
            pickingDate = picking,
            pickFrom = form?.priceDate ?: today,
            isConfirmingDelete = confirming,
            fundSearch = search,
            goldFailed = goldFailed,
            purchases = purchases.map {
                PurchaseRow(
                    transactionId = it.transaction.id,
                    title = titleOf(it),
                    date = shortDateLabel(it.transaction.occurredAt.atZone(domain.zone()).toLocalDate(), today),
                    amount = Money.format(it.transaction.amountMinor, it.transaction.currency)
                )
            },
            paid = purchases.takeIf { it.isNotEmpty() }?.let { list -> Money.format(Money.sum(list.map { it.transaction.amountMinor }), currency) }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditAssetUiState())

    fun onBackClick() = navigator.popBackStack()
    fun onTypeClick(type: AssetType) = edit { it.copy(type = type) }
    fun onNameChange(text: String) = edit { it.copy(name = text) }
    fun onQuantityChange(text: String) = edit { it.copy(quantity = text) }
    fun onKaratClick(karat: Int) = edit { it.copy(karat = karat) }
    fun onPriceChange(text: String) = edit { it.copy(unitPrice = text) }
    fun onValueChange(text: String) = edit { it.copy(value = text) }
    fun onCostChange(text: String) = edit { it.copy(cost = text) }
    fun onSpreadChange(text: String) = edit { it.copy(spread = text) }
    fun onDepreciationChange(text: String) = edit { it.copy(depreciation = text) }
    fun onDateClick() = picking.update { true }
    fun onDateDismiss() = picking.update { false }
    fun onDatePicked(date: LocalDate) {
        picking.update { false }
        edit { it.copy(priceDate = date) }
    }

    /** "Price from the market" on a fund: the list to choose it from. */
    fun onLinkFundClick() {
        search.value = FundSearch(query = form.value?.name.orEmpty())
        viewModelScope.launch {
            funds = funds ?: domain.funds()
            filter()
        }
    }

    fun onFundQueryChange(text: String) {
        search.update { it?.copy(query = text) }
        filter()
    }

    private fun filter() {
        val all = funds
        search.update { current ->
            current ?: return@update null
            if (all == null) return@update current.copy(results = emptyList(), failed = true)
            val words = current.query.lowercase().split(' ').filter { it.isNotBlank() }
            val today = domain.today()
            current.copy(
                failed = false,
                results = all.filter { fund -> words.all { fund.name.lowercase().contains(it) || fund.manager.orEmpty().lowercase().contains(it) } }
                    .take(MAX_RESULTS)
                    .map { FundOption(it.id, it.name, it.manager, it.price.toPlainString(), it.date?.let { d -> shortDateLabel(d, today) }) }
            )
        }
    }

    fun onFundSearchDismiss() = search.update { null }

    fun onFundPicked(id: Long) {
        val fund = funds?.firstOrNull { it.id == id } ?: return
        search.update { null }
        edit {
            it.copy(
                name = it.name.ifBlank { fund.name },
                unitPrice = fund.price.toPlainString(),
                priceDate = fund.date ?: it.priceDate,
                priceSource = PriceProtocol.fundSource(fund.id)
            )
        }
    }

    /** Gold: follow the market price, or keep the one you type. */
    fun onGoldMarketClick(market: Boolean) {
        goldFailed.update { false }
        if (!market) return edit { it.copy(priceSource = null) }
        viewModelScope.launch {
            val gold = domain.gold() ?: return@launch goldFailed.update { true }
            edit { it.copy(unitPrice = gold.perGram.toPlainString(), priceDate = gold.date, priceSource = PriceProtocol.GOLD_SOURCE) }
        }
    }

    fun onPurchaseClick(transactionId: Long) = navigator.navigate(Screen.Transaction(transactionId))

    fun onUnlinkClick() = edit { it.copy(priceSource = null) }

    fun onDeleteClick() = confirmingDelete.update { true }
    fun onDeleteDismiss() = confirmingDelete.update { false }
    fun onDeleteConfirm() {
        confirmingDelete.update { false }
        val currency = form.value?.currency ?: return
        viewModelScope.launch {
            domain.delete(id, currency)
            navigator.popBackStack()
        }
    }

    fun onSaveClick() {
        val current = form.value ?: return
        saving.launch(viewModelScope) {
            val found = domain.save(id, current)
            problems.update { found }
            if (found.isEmpty()) navigator.popBackStack()
            found.isEmpty()
        }
    }

    private companion object {
        const val MAX_RESULTS = 30
    }

    private fun edit(change: (AssetForm) -> AssetForm) {
        form.update { it?.let(change) }
        problems.update { emptySet() }
    }
}
