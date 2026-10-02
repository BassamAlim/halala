package bassamalim.halala.features.merchant

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import bassamalim.halala.core.Globals
import bassamalim.halala.core.domain.Identification
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.feedOf
import bassamalim.halala.core.domain.toItem
import bassamalim.halala.core.enums.BusinessType
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.initialOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MerchantViewModel @Inject constructor(
    private val domain: MerchantDomain,
    private val navigator: Navigator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val id = savedStateHandle.toRoute<Screen.Merchant>().id

    private val sheet = MutableStateFlow<MerchantSheet?>(null)

    val uiState: StateFlow<MerchantUiState> = combine(
        // What it is reads against the categories, which change with it.
        combine(domain.observeMerchant(id), domain.observeCategories(), ::Pair),
        domain.observeAliases(id),
        domain.observeMerchants(),
        domain.observeTransactions(),
        sheet
    ) { (merchant, categories), aliases, merchants, details, sheet ->
        // Gone (merged into another): the screen stays as it was while it leaves.
        if (merchant == null) return@combine MerchantUiState(isLoading = true)

        val zone = domain.zone()
        val today = domain.today()
        val mine = feedOf(details.filter { it.merchantId == id })
        val currency = Globals.PRIMARY_CURRENCY

        MerchantUiState(
            isLoading = false,
            name = merchant.name,
            initial = initialOf(merchant.name),
            spent = Money.format(MerchantDomain.spent(mine, currency), currency, decimals = false),
            currency = currency,
            count = mine.size,
            since = mine.minOfOrNull { it.transaction.occurredAt }?.atZone(zone)?.year?.toString().orEmpty(),
            businessType = merchant.businessType,
            identifiedBy = merchant.identifiedBy,
            confidence = merchant.confidence,
            filesUnder = Identification.categoryFor(merchant.businessType, categories)?.name,
            spellings = aliases.map { SpellingRow(it.alias.id, it.alias.descriptor, it.alias.matchedBy, it.transactions) },
            canSplit = aliases.size > 1,
            transactions = mine.map { it.toItem(zone, today) },
            mergeOptions = (sheet as? MerchantSheet.Merge)
                ?.let { MerchantDomain.mergeOptions(merchants, id, it.query) }
                ?.map { MerchantOption(it.merchant.id, it.merchant.name, it.transactions) }
                .orEmpty(),
            sheet = sheet
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MerchantUiState()
    )

    fun onBackClick() = navigator.popBackStack()

    fun onTransactionClick(transactionId: Long) = navigator.navigate(Screen.Transaction(transactionId))

    fun onSheetDismiss() = sheet.update { null }

    fun onRenameClick() = sheet.update { MerchantSheet.Rename(uiState.value.name) }

    fun onNameChange(name: String) = sheet.update { MerchantSheet.Rename(name) }

    fun onRenameSave() {
        val rename = sheet.value as? MerchantSheet.Rename ?: return
        viewModelScope.launch {
            val problem = domain.rename(id, rename.name)
            sheet.update { if (problem == null) null else rename.copy(problem = problem) }
        }
    }

    fun onSpellingClick(spelling: SpellingRow) {
        if (uiState.value.canSplit) sheet.update { MerchantSheet.Split(spelling) }
    }

    fun onSplitConfirm() {
        val split = sheet.value as? MerchantSheet.Split ?: return
        sheet.update { null }
        viewModelScope.launch { domain.split(split.spelling.id) }
    }

    fun onMergeClick() = sheet.update { MerchantSheet.Merge() }

    fun onBusinessTypeClick() = sheet.update { MerchantSheet.BusinessType }

    fun onBusinessTypePick(type: BusinessType) {
        sheet.update { null }
        viewModelScope.launch { domain.setBusinessType(id, type) }
    }

    fun onMergeQueryChange(query: String) = sheet.update { MerchantSheet.Merge(query) }

    fun onMergePick(option: MerchantOption) = sheet.update { MerchantSheet.ConfirmMerge(option) }

    /** This merchant is gone once merged: its page gives way to the one it joined. */
    fun onMergeConfirm() {
        val into = (sheet.value as? MerchantSheet.ConfirmMerge)?.into ?: return
        sheet.update { null }
        viewModelScope.launch {
            domain.merge(id, into.id)
            navigator.popBackStack()
            navigator.navigate(Screen.Merchant(into.id))
        }
    }
}
