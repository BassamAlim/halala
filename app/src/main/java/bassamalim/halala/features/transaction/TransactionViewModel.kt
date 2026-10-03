package bassamalim.halala.features.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.domain.LoanState
import bassamalim.halala.core.domain.Loans
import bassamalim.halala.core.domain.Splits
import bassamalim.halala.core.data.dataSources.room.relations.PersonWithStats
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
import bassamalim.halala.core.utils.shortDateLabel
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

    private val route = savedStateHandle.toRoute<Screen.Transaction>()
    private val id = route.id

    private val confirmingDelete = MutableStateFlow(false)
    private val sheet = MutableStateFlow<TransactionSheet?>(if (route.split) TransactionSheet.Split() else null)

    val uiState: StateFlow<TransactionUiState> = combine(
        combine(domain.observe(id), domain.observeLoans(), domain.observePeople(), ::Triple),
        domain.observeCategories(),
        domain.observeRules(),
        confirmingDelete,
        sheet
    ) { (detail, loans, people), categories, rules, confirming, sheet ->
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
            amountMinor = tx.amountMinor,
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
            merchant = tx.title,
            merchantId = detail.merchantId,
            merchantName = detail.merchantName,
            personId = detail.personId,
            personName = detail.personName,
            loan = loanLinkOf(detail, loans, today),
            canSplit = tx.direction == Direction.DEBIT && tx.kind.countsInTotals && !detail.isInternalTransfer &&
                    detail.sharedMinor == 0L,
            split = splitOf(detail, loans, people),
            people = people.map { PersonChoice(it.person.id, it.person.name) },
            sheet = (sheet as? TransactionSheet.Split)?.let { previewed(it, tx.amountMinor, tx.currency) } ?: sheet
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

    /** The picker's + New category: the Categories form; back here, the new one is in the picker. */
    fun onNewCategoryClick() {
        sheet.update { null }
        navigator.navigate(Screen.Categories(add = true))
    }

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
        (it as? TransactionSheet.MarkLoan)?.copy(dueOn = date, dueLabel = shortDateLabel(date, domain.today()), picking = false) ?: it
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

    fun onSplitClick() = sheet.update { TransactionSheet.Split() }

    private fun editSplit(change: (TransactionSheet.Split) -> TransactionSheet.Split) =
        sheet.update { (it as? TransactionSheet.Split)?.let(change)?.copy(problems = emptySet()) ?: it }

    fun onSplitPersonClick(personId: Long) = editSplit {
        it.copy(selected = if (personId in it.selected) it.selected - personId else it.selected + personId)
    }

    fun onSplitModeClick(byAmount: Boolean) = editSplit { it.copy(byAmount = byAmount) }

    fun onSplitAmountChange(personId: Long, text: String) = editSplit { it.copy(amounts = it.amounts + (personId to text)) }

    fun onSplitNameChange(text: String) = editSplit { it.copy(newName = text) }

    /** Adds someone by name and picks them. */
    fun onSplitAddPerson() {
        val split = sheet.value as? TransactionSheet.Split ?: return
        viewModelScope.launch {
            val personId = domain.addPerson(split.newName) ?: return@launch
            editSplit { it.copy(newName = "", selected = it.selected + personId) }
        }
    }

    fun onSplitConfirm() {
        val split = sheet.value as? TransactionSheet.Split ?: return
        val state = uiState.value
        val total = state.amountMinor
        val shares = sharesOf(split, total)
        val problems = Splits.validate(total, shares)
        if (problems.isNotEmpty()) {
            sheet.update { split.copy(problems = problems) }
            return
        }
        sheet.update { null }
        viewModelScope.launch { domain.split(id, shares.mapValues { it.value!! }) }
    }

    fun onUnsplitClick() = sheet.update { TransactionSheet.Unsplit }

    fun onUnsplitConfirm() {
        sheet.update { null }
        viewModelScope.launch { domain.unsplit(id) }
    }

    /** Each selected person's share in minor units: equal, or as typed (null when not a valid amount). */
    private fun sharesOf(split: TransactionSheet.Split, totalMinor: Long): Map<Long, Long?> {
        val currency = uiState.value.currency
        return split.selected.associateWith { personId ->
            if (split.byAmount) split.amounts[personId]?.let { Money.parse(it, currency) }
            else Splits.equalShare(totalMinor, split.selected.size)
        }
    }

    private fun previewed(split: TransactionSheet.Split, totalMinor: Long, currency: String): TransactionSheet.Split {
        val shares = split.selected.associateWith { personId ->
            if (split.byAmount) split.amounts[personId]?.let { Money.parse(it, currency) } else Splits.equalShare(totalMinor, split.selected.size)
        }
        val known = shares.values.filterNotNull()
        return split.copy(
            preview = shares.mapNotNull { (id, minor) -> minor?.let { id to Money.format(it, currency) } }.toMap(),
            yours = Money.format(Splits.yours(totalMinor, known).coerceAtLeast(0), currency)
        )
    }

    private fun splitOf(detail: TransactionDetail, loans: List<LoanState>, people: List<PersonWithStats>): SplitInfo? {
        if (detail.sharedMinor == 0L) return null
        val names = people.associate { it.person.id to it.person.name }
        val currency = detail.transaction.currency
        val shares = loans.filter { it.loan.splitOf == detail.transaction.id }.map {
            names[it.loan.personId].orEmpty() to Money.format(it.lentMinor, currency)
        }
        return SplitInfo(shares, Money.format(detail.yourMinor, currency), currency)
    }

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
                    lentOn = it.lentAt?.let { at -> shortDateLabel(at.atZone(domain.zone()).toLocalDate(), today) }.orEmpty()
                )
            }
        )
    }
}
