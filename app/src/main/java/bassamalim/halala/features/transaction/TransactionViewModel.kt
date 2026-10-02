package bassamalim.halala.features.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.Rules
import bassamalim.halala.core.domain.titleOf
import bassamalim.halala.core.domain.toneOf
import bassamalim.halala.core.enums.AmountTone
import bassamalim.halala.core.enums.ExpenseType
import bassamalim.halala.core.models.CategoryOption
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.accountLabel
import bassamalim.halala.core.utils.dateLabel
import bassamalim.halala.core.utils.initialOf
import bassamalim.halala.core.utils.timeLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import bassamalim.halala.core.enums.RuleSource

@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val domain: TransactionDomain,
    private val navigator: Navigator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val id = savedStateHandle.toRoute<Screen.Transaction>().id

    private val confirmingDelete = MutableStateFlow(false)
    private val sheet = MutableStateFlow<TransactionSheet?>(null)

    val uiState: StateFlow<TransactionUiState> = combine(
        domain.observe(id),
        domain.observeCategories(),
        domain.observeRules(),
        confirmingDelete,
        sheet
    ) { detail, categories, rules, confirming, sheet ->
        // Gone (deleted from here or elsewhere): the screen stays as it was while it leaves.
        if (detail == null) return@combine TransactionUiState(isLoading = true)

        val tx = detail.transaction
        val zone = domain.zone()
        val today = domain.today()
        val local = tx.occurredAt.atZone(zone)
        val tone = toneOf(detail)
        val here = accountLabel(detail.institutionName, detail.accountNickname)
        val there = detail.counterpartNickname?.let { accountLabel(detail.counterpartInstitutionName, it) }
        val title = titleOf(detail)

        TransactionUiState(
            isLoading = false,
            title = title,
            initial = initialOf(title),
            kind = tx.kind,
            tone = tone,
            amount = Money.format(
                if (tone == AmountTone.Spending) -tx.amountMinor else tx.amountMinor,
                tx.currency,
                showPlus = tone == AmountTone.Income
            ),
            currency = tx.currency,
            whenLabel = "${dateLabel(local.toLocalDate(), today)} · ${timeLabel(local.toLocalTime())}",
            accountLabel = here,
            fromLabel = there?.let { if (detail.isTransferInLeg) it else here },
            toLabel = there?.let { if (detail.isTransferInLeg) here else it },
            note = tx.note,
            source = tx.source,
            createdLabel = dateLabel(tx.createdAt.atZone(zone).toLocalDate(), today),
            isConfirmingDelete = confirming,
            canCategorise = Rules.canCategorise(detail),
            category = tx.categoryId?.let { CategoryOption(it, detail.categoryName.orEmpty()) },
            expenseType = tx.expenseType,
            categories = categories.map { CategoryOption(it.id, it.name) },
            filedBy = rules.firstOrNull { it.stats.rule.id == tx.ruleId }?.let {
                FiledBy(
                    words = it.words,
                    category = it.stats.categoryName.orEmpty(),
                    expenseType = it.stats.rule.actions.expenseType,
                    hits = it.stats.hits,
                    identifiedAs = detail.merchantType.takeIf { _ -> it.stats.rule.source == RuleSource.AI },
                    identifiedBy = detail.merchantIdentifiedBy,
                    confidence = detail.merchantConfidence
                )
            },
            sheet = sheet,
            merchant = tx.title,
            merchantId = detail.merchantId,
            merchantName = detail.merchantName,
            personId = detail.personId,
            personName = detail.personName
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TransactionUiState()
    )

    fun onBackClick() = navigator.popBackStack()

    fun onEditClick() = navigator.navigate(Screen.EditTransaction(id = id))

    fun onCategoryClick() = sheet.update { TransactionSheet.Category }

    fun onTypeClick() = sheet.update { TransactionSheet.Type }

    fun onSheetDismiss() = sheet.update { null }

    /** A named merchant can be remembered, so it asks; a nameless one is filed on its own. */
    fun onCategoryPick(category: CategoryOption) {
        val merchant = uiState.value.merchant
        if (merchant.isBlank()) return onJustThisOne(category)

        viewModelScope.launch {
            val others = domain.othersFrom(merchant, id)
            sheet.update { TransactionSheet.Always(category, others) }
        }
    }

    fun onJustThisOne(category: CategoryOption) {
        sheet.update { null }
        viewModelScope.launch { domain.file(id, category.id) }
    }

    fun onAlways(category: CategoryOption) {
        val merchant = uiState.value.merchant
        sheet.update { null }
        viewModelScope.launch { domain.fileAlways(id, merchant, category.id) }
    }

    fun onTypePick(type: ExpenseType) {
        val categoryId = uiState.value.category?.id
        sheet.update { null }
        viewModelScope.launch { domain.setType(id, categoryId, type) }
    }

    fun onEditRuleClick() = navigator.navigate(Screen.Rules)

    fun onPersonClick() {
        val personId = uiState.value.personId ?: return
        navigator.navigate(Screen.Person(personId))
    }

    fun onMerchantClick() {
        val merchantId = uiState.value.merchantId ?: return
        navigator.navigate(Screen.Merchant(merchantId))
    }

    fun onDeleteClick() = confirmingDelete.update { true }

    fun onDeleteDismiss() = confirmingDelete.update { false }

    fun onDeleteConfirm() {
        confirmingDelete.update { false }
        viewModelScope.launch {
            domain.delete(id)
            navigator.popBackStack()
        }
    }
}
