package bassamalim.halala.features.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.domain.LoanState
import bassamalim.halala.core.domain.Loans
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.Rules
import bassamalim.halala.core.domain.titleOf
import bassamalim.halala.core.domain.toneOf
import bassamalim.halala.core.enums.AmountTone
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.ExpenseType
import bassamalim.halala.core.enums.LoanDirection
import bassamalim.halala.core.enums.LoanEventType
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
import java.time.LocalDate
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
        combine(domain.observe(id), domain.observeLoans(), ::Pair),
        domain.observeCategories(),
        domain.observeRules(),
        confirmingDelete,
        sheet
    ) { (detail, loans), categories, rules, confirming, sheet ->
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
            personName = detail.personName,
            loan = loanLinkOf(detail, loans, today)
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

    fun onMarkLoanClick() = sheet.update { TransactionSheet.MarkLoan(pickFrom = domain.today().plusMonths(1)) }

    fun onDueClick() = sheet.update { (it as? TransactionSheet.MarkLoan)?.copy(picking = true) ?: it }

    fun onDuePicked(date: LocalDate) = sheet.update {
        (it as? TransactionSheet.MarkLoan)?.copy(dueOn = date, dueLabel = dateLabel(date, domain.today()), picking = false) ?: it
    }

    fun onDuePickDismiss() = sheet.update { (it as? TransactionSheet.MarkLoan)?.copy(picking = false) ?: it }

    fun onMarkLoanConfirm() {
        val mark = sheet.value as? TransactionSheet.MarkLoan ?: return
        sheet.update { null }
        viewModelScope.launch { domain.openLoan(id, mark.dueOn) }
    }

    fun onRepaysClick() {
        val suggestion = (uiState.value.loan as? LoanLink.Open)?.suggestion ?: return
        viewModelScope.launch { domain.repay(suggestion.loanId, id) }
    }

    fun onUnlinkClick() = sheet.update { TransactionSheet.Unlink }

    fun onUnlinkConfirm() {
        sheet.update { null }
        viewModelScope.launch { domain.unlinkLoan(id) }
    }

    fun onLoanClick() {
        val part = uiState.value.loan as? LoanLink.Part ?: return
        navigator.navigate(Screen.Person(part.personId))
    }

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

    private fun loanLinkOf(detail: TransactionDetail, loans: List<LoanState>, today: LocalDate): LoanLink? {
        val tx = detail.transaction
        val personId = detail.personId ?: return null
        val person = detail.personName.orEmpty()

        val part = loans.firstOrNull { state -> state.events.any { it.transactionId == tx.id } }
        if (part != null) {
            val event = part.events.first { it.transactionId == tx.id }
            return LoanLink.Part(
                lent = part.loan.direction == LoanDirection.LENT,
                repays = event.type != LoanEventType.DISBURSEMENT,
                person = person,
                personId = part.loan.personId,
                remaining = Money.format(part.remainingMinor, part.loan.currency),
                currency = part.loan.currency,
                settled = !part.isOpen
            )
        }
        if (tx.kind !in Loans.MARKABLE) return null

        val repaid = Loans.repaidBy(personId, tx.direction, tx.currency, tx.kind, loans)
        return LoanLink.Open(
            lent = tx.direction == Direction.DEBIT,
            person = person,
            suggestion = repaid?.let {
                Suggestion(
                    loanId = it.loan.id,
                    lent = it.loan.direction == LoanDirection.LENT,
                    remaining = Money.format(it.remainingMinor, it.loan.currency),
                    currency = it.loan.currency,
                    lentOn = it.lentAt?.let { at -> dateLabel(at.atZone(domain.zone()).toLocalDate(), today) }.orEmpty()
                )
            }
        )
    }
}
