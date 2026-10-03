package bassamalim.halala.features.savings

import bassamalim.halala.core.utils.OneAtATime
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import bassamalim.halala.core.Globals
import bassamalim.halala.core.data.dataSources.room.entities.SavingsTerms
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.GoalsRepository
import bassamalim.halala.core.data.repositories.SavingsRepository
import bassamalim.halala.core.domain.Assets
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.Savings
import bassamalim.halala.core.enums.MaturityChoice
import bassamalim.halala.core.enums.SavingsKind
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.accountLabel
import bassamalim.halala.core.utils.shortDateLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** One savings account as the board shows it; figures summary style, labels already words. */
data class SavingsCard(
    val accountId: Long,
    /** Set for a term deposit, which opens its own form. */
    val depositId: Long? = null,
    val name: String,
    val kind: SavingsKind?,
    val rate: String?,
    val balance: String,
    /** Awaeed: tenor, maturity, start, progress, profit, what happens at maturity. */
    val tenorMonths: Int? = null,
    val maturity: String? = null,
    val started: String? = null,
    val progress: Float = 0f,
    val profit: String? = null,
    val choice: MaturityChoice? = null,
    /** Hasad: the month's lowest balance and next month's profit on it, paid on [profitOn]. */
    val lowest: String? = null,
    val profitOn: String? = null,
    /** A deposit: the goal it is for. */
    val goal: String? = null
)

data class SavingsUiState(val isLoading: Boolean = true, val total: String = "", val cards: List<SavingsCard> = emptyList())

@HiltViewModel
class SavingsViewModel @Inject constructor(
    savingsRepository: SavingsRepository,
    private val navigator: Navigator,
    private val clock: Clock
) : ViewModel() {

    val uiState: StateFlow<SavingsUiState> = combine(savingsRepository.observe(), savingsRepository.observeDeposits()) { accounts, deposits ->
        val today = LocalDate.now(clock)
        val c = Globals.PRIMARY_CURRENCY
        SavingsUiState(
            isLoading = false,
            total = Money.format(
                Money.sum(accounts.filter { it.account.account.currency == c }.map { it.account.balanceMinor } + deposits.filter { it.currency == c }.map { it.amountMinor }),
                c,
                decimals = false
            ),
            cards = deposits.map { held ->
                SavingsCard(
                    accountId = 0,
                    depositId = held.deposit.id,
                    name = "",
                    kind = SavingsKind.AWAEED,
                    rate = held.deposit.ratePercent,
                    balance = Money.format(held.amountMinor, held.currency, decimals = false),
                    tenorMonths = held.deposit.tenorMonths,
                    maturity = held.term?.maturity?.let { shortDateLabel(it, today) },
                    started = shortDateLabel(held.term?.start ?: held.start, today),
                    progress = held.term?.elapsed ?: 0f,
                    profit = held.term?.takeIf { held.deposit.ratePercent != null }?.let { Money.format(it.expectedProfitMinor, held.currency, decimals = false) },
                    choice = held.deposit.maturityChoice,
                    goal = held.goalName
                )
            } + accounts.map { saved ->
                val account = saved.account
                val currency = account.account.currency
                val terms = saved.terms
                SavingsCard(
                    accountId = account.account.id,
                    name = accountLabel(account.institutionName, account.account.nickname),
                    kind = terms?.kind,
                    rate = terms?.ratePercent,
                    balance = Money.format(account.balanceMinor, currency, decimals = false),
                    tenorMonths = terms?.tenorMonths,
                    maturity = saved.term?.maturity?.let { shortDateLabel(it, today) },
                    started = saved.term?.start?.let { shortDateLabel(it, today) },
                    progress = saved.term?.elapsed ?: 0f,
                    profit = when (terms?.kind) {
                        SavingsKind.AWAEED -> saved.term?.let { Money.format(it.expectedProfitMinor, currency, decimals = false) }
                        SavingsKind.HASAD -> Money.format(Savings.hasadProfit(terms, saved.lowestThisMonthMinor, Money.fractionDigits(currency)), currency, decimals = false)
                        null -> null
                    },
                    choice = terms?.maturityChoice,
                    lowest = Money.format(saved.lowestThisMonthMinor, currency, decimals = false).takeIf { terms?.kind == SavingsKind.HASAD },
                    profitOn = shortDateLabel(today.withDayOfMonth(1).plusMonths(1), today).takeIf { terms?.kind == SavingsKind.HASAD }
                )
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavingsUiState())

    fun onBackClick() = navigator.popBackStack()

    fun onCardClick(card: SavingsCard) =
        navigator.navigate(card.depositId?.let { Screen.Deposit(it) } ?: Screen.SavingsTerms(card.accountId))
}

/** The terms of one savings account, as the form holds them. */
data class TermsForm(
    val kind: SavingsKind = SavingsKind.AWAEED,
    val rate: String = "",
    val start: LocalDate? = null,
    val tenorMonths: Int = 6,
    val choice: MaturityChoice = MaturityChoice.RENEW_WITH_PROFIT
)

data class TermsUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val form: TermsForm = TermsForm(),
    val startLabel: String? = null,
    val rateInvalid: Boolean = false,
    val picking: Boolean = false,
    val pickFrom: LocalDate = LocalDate.MIN,
    val hasTerms: Boolean = false
)

@HiltViewModel
class SavingsTermsViewModel @Inject constructor(
    private val savingsRepository: SavingsRepository,
    accountsRepository: AccountsRepository,
    private val navigator: Navigator,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val saving = OneAtATime()

    private val accountId = savedStateHandle.toRoute<Screen.SavingsTerms>().accountId
    private val form = MutableStateFlow<TermsForm?>(null)
    private val invalid = MutableStateFlow(false)
    private val picking = MutableStateFlow(false)
    private var existed = false

    init {
        viewModelScope.launch {
            val terms = savingsRepository.get(accountId)
            existed = terms != null
            form.value = terms?.let {
                TermsForm(it.kind, it.ratePercent, it.startDate, it.tenorMonths ?: 6, it.maturityChoice ?: MaturityChoice.RENEW_WITH_PROFIT)
            } ?: TermsForm(start = LocalDate.now(clock))
        }
    }

    val uiState: StateFlow<TermsUiState> = combine(form, invalid, picking, accountsRepository.observeAll()) { form, invalid, picking, accounts ->
        val today = LocalDate.now(clock)
        val account = accounts.firstOrNull { it.account.id == accountId }
        TermsUiState(
            isLoading = form == null,
            name = account?.let { accountLabel(it.institutionName, it.account.nickname) }.orEmpty(),
            form = form ?: TermsForm(),
            startLabel = form?.start?.let { shortDateLabel(it, today) },
            rateInvalid = invalid,
            picking = picking,
            pickFrom = form?.start ?: today,
            hasTerms = existed
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TermsUiState())

    private fun edit(change: (TermsForm) -> TermsForm) {
        form.update { it?.let(change) }
        invalid.update { false }
    }

    fun onBackClick() = navigator.popBackStack()
    fun onKindClick(kind: SavingsKind) = edit { it.copy(kind = kind) }
    fun onRateChange(text: String) = edit { it.copy(rate = text) }
    fun onTenorClick(months: Int) = edit { it.copy(tenorMonths = months) }
    fun onChoiceClick(choice: MaturityChoice) = edit { it.copy(choice = choice) }
    fun onStartClick() = picking.update { true }
    fun onStartDismiss() = picking.update { false }
    fun onStartPicked(date: LocalDate) {
        picking.update { false }
        edit { it.copy(start = date) }
    }

    fun onSaveClick() {
        val current = form.value ?: return
        val rate = Assets.decimal(current.rate)?.takeIf { it <= BigDecimal(100) }
        if (rate == null) return invalid.update { true }
        val awaeed = current.kind == SavingsKind.AWAEED
        saving.launch(viewModelScope) {
            savingsRepository.put(
                SavingsTerms(
                    accountId = accountId,
                    kind = current.kind,
                    ratePercent = rate.stripTrailingZeros().toPlainString(),
                    startDate = current.start.takeIf { awaeed },
                    tenorMonths = current.tenorMonths.takeIf { awaeed },
                    maturityChoice = current.choice.takeIf { awaeed }
                )
            )
            navigator.popBackStack()
            true
        }
    }

    fun onClearClick() {
        saving.launch(viewModelScope) {
            savingsRepository.delete(accountId)
            navigator.popBackStack()
            true
        }
    }

    companion object {
        /** Al Rajhi's Awaeed runs from one to 36 months. */
        val TENORS = listOf(1, 3, 6, 12, 24, 36)
    }
}

/** One term deposit, as its form holds it. An empty rate is one you haven't given yet. */
data class DepositForm(
    val rate: String = "",
    val tenorMonths: Int? = null,
    val choice: MaturityChoice? = null,
    val goalId: Long? = null
)

data class DepositUiState(
    val isLoading: Boolean = true,
    val amount: String = "",
    val started: String = "",
    val form: DepositForm = DepositForm(),
    /** Your goals to file it under; a null id is "none". */
    val goals: List<Pair<Long?, String>> = emptyList(),
    val rateInvalid: Boolean = false,
    val confirmingPayOut: Boolean = false
)

@HiltViewModel
class DepositViewModel @Inject constructor(
    private val savingsRepository: SavingsRepository,
    goalsRepository: GoalsRepository,
    private val navigator: Navigator,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val saving = OneAtATime()

    private val id = savedStateHandle.toRoute<Screen.Deposit>().id
    private val form = MutableStateFlow<DepositForm?>(null)
    private val invalid = MutableStateFlow(false)
    private val confirming = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            form.value = savingsRepository.getDeposit(id)?.let { DepositForm(it.ratePercent.orEmpty(), it.tenorMonths, it.maturityChoice, it.goalId) }
        }
    }

    val uiState: StateFlow<DepositUiState> = combine(
        form, invalid, confirming, savingsRepository.observeDeposits(), goalsRepository.observeStates()
    ) { form, invalid, confirming, deposits, goals ->
        val held = deposits.firstOrNull { it.deposit.id == id }
        DepositUiState(
            isLoading = form == null || held == null,
            amount = held?.let { Money.format(it.amountMinor, it.currency, decimals = false) }.orEmpty(),
            started = held?.let { shortDateLabel(it.start, LocalDate.now(clock)) }.orEmpty(),
            form = form ?: DepositForm(),
            goals = listOf<Pair<Long?, String>>(null to "") + goals.map { it.goal.id to it.goal.name },
            rateInvalid = invalid,
            confirmingPayOut = confirming
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DepositUiState())

    private fun edit(change: (DepositForm) -> DepositForm) {
        form.update { it?.let(change) }
        invalid.update { false }
    }

    fun onBackClick() = navigator.popBackStack()
    fun onRateChange(text: String) = edit { it.copy(rate = text) }
    fun onTenorClick(months: Int) = edit { it.copy(tenorMonths = months) }
    fun onChoiceClick(choice: MaturityChoice) = edit { it.copy(choice = choice) }
    fun onGoalClick(goalId: Long?) = edit { it.copy(goalId = goalId) }
    fun onNewGoalClick() = navigator.navigate(Screen.EditGoal())
    fun onPayOutClick() = confirming.update { true }
    fun onPayOutDismiss() = confirming.update { false }

    fun onSaveClick() {
        val current = form.value ?: return
        val rate = current.rate.trim().takeIf { it.isNotEmpty() }?.let { text ->
            Assets.decimal(text)?.takeIf { it <= BigDecimal(100) } ?: return invalid.update { true }
        }
        saving.launch(viewModelScope) {
            savingsRepository.getDeposit(id)?.let {
                savingsRepository.saveDeposit(
                    it.copy(
                        ratePercent = rate?.stripTrailingZeros()?.toPlainString(),
                        tenorMonths = current.tenorMonths,
                        maturityChoice = current.choice,
                        goalId = current.goalId
                    )
                )
            }
            navigator.popBackStack()
            true
        }
    }

    fun onPayOutConfirm() {
        confirming.update { false }
        saving.launch(viewModelScope) {
            savingsRepository.payOut(id)
            navigator.popBackStack()
            true
        }
    }
}
