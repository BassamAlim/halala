package bassamalim.halala.features.moneyFlow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.FlowPart
import bassamalim.halala.core.domain.MoneyFlow
import bassamalim.halala.core.domain.PartKind
import bassamalim.halala.core.domain.titleOf
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.accountLabel
import bassamalim.halala.core.utils.monthLabel
import bassamalim.halala.core.utils.shortDateLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * One part of the flow, worded: [name] is the account's or category's (null for the others,
 * which the screen names by [kind]); [percent] of what came in (or went out, when that was more);
 * [change] against the month before; [items] the transactions that make it up, biggest first.
 * [weight] is minor units, for drawing only.
 */
data class FlowPartUi(
    val key: String,
    val kind: PartKind,
    val name: String?,
    val amount: String,
    val weight: Long,
    val percent: Int,
    val change: Int?,
    val items: List<FlowItem>,
    val more: Int
)

/** One transaction in a part. */
data class FlowItem(val id: Long, val title: String, val day: String, val amount: String)

/** A leg with its other side missing, worded for the card. */
data class UnmatchedLeg(val id: Long, val amount: String, val account: String, val day: String, val outgoing: Boolean)

data class Choice(val id: Long, val label: String)

sealed interface FlowSheet {
    data object Month : FlowSheet
    data object Account : FlowSheet
    data class PickAccount(val legId: Long, val options: List<Choice>) : FlowSheet
}

data class MoneyFlowUiState(
    val isLoading: Boolean = true,
    val month: YearMonth = YearMonth.of(2000, 1),
    val monthName: String = "",
    val months: List<Pair<YearMonth, String>> = emptyList(),
    val accountId: Long? = null,
    val accountName: String = "",
    val accounts: List<Choice> = emptyList(),
    val currency: String = "",
    /** The salary's day when the headline is a salary, else null and it is everything that came in. */
    val salaryDay: String? = null,
    val headline: String = "",
    val moved: String? = null,
    /** Came in, and went out: the two ends of the chart, each summing to the same. */
    val sources: List<FlowPartUi> = emptyList(),
    val uses: List<FlowPartUi> = emptyList(),
    /** Whole percents of the total: spent (with what went to people), and kept (moved, saved or left). */
    val spentPercent: Int = 0,
    val keptPercent: Int = 0,
    /** Spending against the month before, whole percent; null with nothing then. */
    val spentChange: Int? = null,
    /** The part tapped on the chart or in the lists. */
    val selected: String? = null,
    val unmatched: List<UnmatchedLeg> = emptyList(),
    val sheet: FlowSheet? = null
)

/**
 * Money flow, the Activity board's second segment: for one account and month, what came in and
 * where it went, as a Sankey, and the moves with one side missing.
 */
@HiltViewModel
class MoneyFlowViewModel @Inject constructor(
    accountsRepository: AccountsRepository,
    private val transactionsRepository: TransactionsRepository,
    private val classificationRepository: ClassificationRepository,
    private val navigator: Navigator,
    private val clock: Clock
) : ViewModel() {

    private data class Picks(
        val month: YearMonth? = null,
        val accountId: Long? = null,
        val sheet: FlowSheet? = null,
        val selected: String? = null
    )

    private val picks = MutableStateFlow(Picks())

    val uiState: StateFlow<MoneyFlowUiState> = combine(
        accountsRepository.observeAll(),
        transactionsRepository.observeAll(),
        picks
    ) { accounts, details, picks ->
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val month = picks.month ?: YearMonth.from(today)
        val open = accounts.filter { !it.account.archived && it.account.type.listed }
        val accountId = picks.accountId?.takeIf { id -> open.any { it.account.id == id } }
            ?: MoneyFlow.startingAccount(details, month, zone)
            ?: open.firstOrNull()?.account?.id
        val account = open.firstOrNull { it.account.id == accountId }
        val names = accounts.associate { it.account.id to accountLabel(it.institutionName, it.account.nickname) }
        val currency = account?.account?.currency.orEmpty()
        val flow = accountId?.let { MoneyFlow.of(details, it, month, zone) }
        val before = accountId?.let { MoneyFlow.of(details, it, month.minusMonths(1), zone) }
        fun f(minor: Long) = Money.format(minor, currency, decimals = false)
        val byId = details.associateBy { it.transaction.id }
        val total = flow?.totalMinor ?: 0
        val previous = before?.let { (it.sources + it.uses).associate { part -> part.key to part.minor } }.orEmpty()

        fun worded(part: FlowPart) = FlowPartUi(
            key = part.key,
            kind = part.kind,
            name = part.accountId?.let { names[it] } ?: part.name,
            amount = f(part.minor),
            weight = part.minor,
            percent = MoneyFlow.percentOf(part.minor, total),
            change = MoneyFlow.change(part.minor, previous[part.key]),
            items = part.transactionIds.take(ITEMS).mapNotNull(byId::get).map {
                FlowItem(
                    id = it.transaction.id,
                    title = titleOf(it),
                    day = shortDateLabel(it.transaction.occurredAt.atZone(zone).toLocalDate(), today),
                    amount = Money.format(it.transaction.amountMinor, it.transaction.currency)
                )
            },
            more = (part.transactionIds.size - ITEMS).coerceAtLeast(0)
        )
        val uses = flow?.uses.orEmpty()
        val spentMinor = Money.sum(uses.filter { it.kind.spending }.map { it.minor })
        val spentBefore = before?.uses?.filter { it.kind.spending }?.let { list -> Money.sum(list.map { it.minor }) }
        val first = details.minOfOrNull { it.transaction.occurredAt }?.atZone(zone)?.let(YearMonth::from) ?: YearMonth.from(today)
        MoneyFlowUiState(
            isLoading = false,
            month = month,
            monthName = monthLabel(month, today),
            months = generateSequence(YearMonth.from(today)) { it.minusMonths(1) }
                .takeWhile { !it.isBefore(first) }.take(MONTHS)
                .map { it to monthLabel(it, today) }.toList(),
            accountId = accountId,
            accountName = account?.let { names[it.account.id] }.orEmpty(),
            accounts = open.map { Choice(it.account.id, names[it.account.id].orEmpty()) },
            currency = currency,
            salaryDay = flow?.salaryOn?.takeIf { flow.salaryMinor > 0 }?.let { shortDateLabel(it, today) },
            headline = flow?.let { f(if (it.salaryMinor > 0) it.salaryMinor else it.inMinor) }.orEmpty(),
            moved = flow?.movedMinor?.takeIf { it > 0 }?.let(::f),
            sources = flow?.sources.orEmpty().map(::worded),
            uses = uses.map(::worded),
            spentPercent = MoneyFlow.percentOf(spentMinor, total),
            keptPercent = MoneyFlow.percentOf(Money.sum(uses.filter { it.kind.kept }.map { it.minor }), total),
            spentChange = MoneyFlow.change(spentMinor, spentBefore),
            selected = picks.selected?.takeIf { key -> (flow?.sources.orEmpty() + uses).any { it.key == key } },
            unmatched = MoneyFlow.unmatched(details).map {
                UnmatchedLeg(
                    id = it.transaction.id,
                    amount = Money.format(it.transaction.amountMinor, it.transaction.currency),
                    account = names[it.transaction.accountId].orEmpty(),
                    day = shortDateLabel(it.transaction.occurredAt.atZone(zone).toLocalDate(), today),
                    outgoing = it.transaction.direction == Direction.DEBIT
                )
            },
            sheet = picks.sheet
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoneyFlowUiState())

    fun onMonthClick() = picks.update { it.copy(sheet = FlowSheet.Month) }
    fun onAccountClick() = picks.update { it.copy(sheet = FlowSheet.Account) }
    fun onSheetDismiss() = picks.update { it.copy(sheet = null) }
    fun onMonthPicked(month: YearMonth) = picks.update { it.copy(month = month, sheet = null, selected = null) }
    fun onAccountPicked(id: Long) = picks.update { it.copy(accountId = id, sheet = null, selected = null) }

    /** Tapping a part shows what makes it up; tapping it again lets go. */
    fun onPartClick(key: String) = picks.update { it.copy(selected = key.takeIf { k -> k != it.selected }) }

    fun onItemClick(id: Long) = navigator.navigate(Screen.Transaction(id))

    /** "It went to someone": a plain transfer, then its detail to say who. */
    fun onWentToSomeone(legId: Long) {
        viewModelScope.launch {
            transactionsRepository.markExternal(legId)
            classificationRepository.applyRules()
            navigator.navigate(Screen.Transaction(legId))
        }
    }

    /** "Pick the account": the accounts in its currency it could have gone to. */
    fun onPickAccount(legId: Long) {
        viewModelScope.launch {
            val leg = transactionsRepository.get(legId) ?: return@launch
            val options = uiState.value.accounts.filter { it.id != leg.accountId }
            val same = options.filter { choice -> transactionsRepository.currencyOf(choice.id) == leg.currency }
            picks.update { it.copy(sheet = FlowSheet.PickAccount(legId, same)) }
        }
    }

    fun onCounterpartPicked(legId: Long, accountId: Long) {
        picks.update { it.copy(sheet = null) }
        viewModelScope.launch { transactionsRepository.completeMove(legId, accountId) }
    }

    private companion object {
        const val MONTHS = 24

        /** Transactions shown under a part when it is tapped. */
        const val ITEMS = 5
    }
}
