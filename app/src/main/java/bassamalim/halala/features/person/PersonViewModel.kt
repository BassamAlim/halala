package bassamalim.halala.features.person

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import bassamalim.halala.core.Globals
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.domain.LoanState
import bassamalim.halala.core.domain.Loans
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.feedOf
import bassamalim.halala.core.domain.toItem
import bassamalim.halala.core.enums.AmountTone
import bassamalim.halala.core.enums.LoanDirection
import bassamalim.halala.core.enums.LoanEventType
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.accountLabel
import bassamalim.halala.core.utils.shortDateLabel
import bassamalim.halala.core.utils.initialOf
import bassamalim.halala.features.people.PeopleDomain
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class PersonViewModel @Inject constructor(
    private val domain: PersonDomain,
    private val navigator: Navigator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val id = savedStateHandle.toRoute<Screen.Person>().id

    private val sheet = MutableStateFlow<PersonSheet?>(null)

    val uiState: StateFlow<PersonUiState> = combine(
        domain.observePerson(id),
        domain.observeAliases(id),
        domain.observePeople(),
        combine(domain.observeTransactions(), domain.observeLoans(), ::Pair),
        sheet
    ) { person, aliases, people, (details, loans), sheet ->
        // Gone (merged into someone else): the screen stays as it was while it leaves.
        if (person == null) return@combine PersonUiState(isLoading = true)

        val zone = domain.zone()
        val today = domain.today()
        val mine = feedOf(details.filter { it.personId == id })
        val currency = Globals.PRIMARY_CURRENCY
        val flow = PeopleDomain.flowsByPerson(mine, currency)[id]
        val net = flow?.netMinor ?: 0

        PersonUiState(
            isLoading = false,
            name = person.name,
            initial = initialOf(person.name),
            currency = currency,
            sent = Money.format(flow?.sentMinor ?: 0, currency, decimals = false),
            received = Money.format(flow?.receivedMinor ?: 0, currency, decimals = false),
            net = Money.format(net, currency, decimals = false, showPlus = net > 0),
            netTone = if (net > 0) AmountTone.Income else AmountTone.Spending,
            count = mine.size,
            spellings = aliases.map { SpellingRow(it.alias.id, it.alias.descriptor, it.transactions) },
            canSplit = aliases.size > 1,
            transactions = mine.map { it.toItem(zone, today) },
            loans = loans
                .filter { it.loan.personId == id }
                .sortedWith(compareBy({ !it.isOpen }, { it.loan.dueOn ?: LocalDate.MAX }, { it.lentAt }))
                .map { state -> loanOf(state, mine, zone, today) },
            mergeOptions = (sheet as? PersonSheet.Merge)
                ?.let { PersonDomain.mergeOptions(people, id, it.query) }
                ?.map { PersonOption(it.person.id, it.person.name, it.transactions) }
                .orEmpty(),
            sheet = sheet
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PersonUiState()
    )

    fun onBackClick() = navigator.popBackStack()

    fun onTransactionClick(transactionId: Long) = navigator.navigate(Screen.Transaction(transactionId))

    fun onSheetDismiss() = sheet.update { null }

    fun onRepayClick(loanId: Long) = sheet.update { PersonSheet.Repay(loanId) }

    fun onRepayPick(transactionId: Long) {
        val repay = sheet.value as? PersonSheet.Repay ?: return
        sheet.update { null }
        viewModelScope.launch { domain.repay(repay.loanId, transactionId) }
    }

    fun onForgiveClick() {
        val repay = sheet.value as? PersonSheet.Repay ?: return
        sheet.update { PersonSheet.Forgive(repay.loanId) }
    }

    fun onForgiveConfirm() {
        val forgive = sheet.value as? PersonSheet.Forgive ?: return
        sheet.update { null }
        viewModelScope.launch { domain.forgive(forgive.loanId) }
    }

    fun onDueClick(loanId: Long) {
        val loan = uiState.value.loans.firstOrNull { it.loanId == loanId } ?: return
        sheet.update { PersonSheet.Due(loanId, loan.pickDueFrom) }
    }

    fun onDuePicked(date: LocalDate) {
        val due = sheet.value as? PersonSheet.Due ?: return
        sheet.update { null }
        viewModelScope.launch { domain.setDueOn(due.loanId, date) }
    }

    fun onRenameClick() = sheet.update { PersonSheet.Rename(uiState.value.name) }

    fun onNameChange(name: String) = sheet.update { PersonSheet.Rename(name) }

    fun onRenameSave() {
        val rename = sheet.value as? PersonSheet.Rename ?: return
        viewModelScope.launch {
            val problem = domain.rename(id, rename.name)
            sheet.update { if (problem == null) null else rename.copy(problem = problem) }
        }
    }

    fun onSpellingClick(spelling: SpellingRow) {
        if (uiState.value.canSplit) sheet.update { PersonSheet.Split(spelling) }
    }

    fun onSplitConfirm() {
        val split = sheet.value as? PersonSheet.Split ?: return
        sheet.update { null }
        viewModelScope.launch { domain.split(split.spelling.id) }
    }

    fun onMergeClick() = sheet.update { PersonSheet.Merge() }

    fun onMergeQueryChange(query: String) = sheet.update { PersonSheet.Merge(query) }

    fun onMergePick(option: PersonOption) = sheet.update { PersonSheet.ConfirmMerge(option) }

    /** This person is gone once merged: their page gives way to the one they joined. */
    fun onMergeConfirm() {
        val into = (sheet.value as? PersonSheet.ConfirmMerge)?.into ?: return
        sheet.update { null }
        viewModelScope.launch {
            domain.merge(id, into.id)
            navigator.popBackStack()
            navigator.navigate(Screen.Person(into.id))
        }
    }

    private fun loanOf(state: LoanState, mine: List<TransactionDetail>, zone: ZoneId, today: LocalDate): PersonLoan {
        val loan = state.loan
        val lent = loan.direction == LoanDirection.LENT
        fun day(at: Instant?) = at?.let { shortDateLabel(it.atZone(zone).toLocalDate(), today) }.orEmpty()

        return PersonLoan(
            loanId = loan.id,
            lent = lent,
            open = state.isOpen,
            remaining = Money.format(state.remainingMinor, loan.currency),
            currency = loan.currency,
            lentTotal = Money.format(state.lentMinor, loan.currency, decimals = false),
            lentOn = day(state.lentAt),
            repaid = Money.format(state.repaidMinor, loan.currency, decimals = false),
            hasRepaid = state.repaidMinor > 0,
            dueLabel = loan.dueOn?.let { shortDateLabel(it, today) },
            pickDueFrom = loan.dueOn ?: today.plusMonths(1),
            progress = if (state.lentMinor == 0L) 0f
            else (Math.subtractExact(state.lentMinor, state.remainingMinor).toDouble() / state.lentMinor).toFloat(),
            settledLabel = state.settledAt?.let { day(it) },
            forgiven = state.forgivenMinor > 0,
            // The latest first, as the board lists them.
            events = state.events.reversed().map { event ->
                // Money in is income-toned and signed; money out plain with a minus; forgiving moves none.
                val incoming = when (event.type) {
                    LoanEventType.DISBURSEMENT -> !lent
                    LoanEventType.REPAYMENT -> lent
                    LoanEventType.FORGIVENESS -> null
                }
                LoanEventItem(
                    transactionId = event.transactionId,
                    type = event.type,
                    day = day(event.at),
                    kind = event.kind,
                    accountLabel = event.accountNickname?.let { accountLabel(event.institutionName, it) },
                    amount = Money.format(
                        if (incoming == false) -event.amountMinor else event.amountMinor,
                        loan.currency,
                        showPlus = incoming == true
                    ),
                    tone = if (incoming == true) AmountTone.Income else AmountTone.Spending
                )
            },
            candidates = if (!state.isOpen) emptyList() else mine
                .filter {
                    it.transaction.kind in Loans.MARKABLE && it.transaction.direction == loan.direction.repaying &&
                            it.transaction.currency == loan.currency
                }
                .map { it.toItem(zone, today) }
        )
    }
}
