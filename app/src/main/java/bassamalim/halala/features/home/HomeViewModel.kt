package bassamalim.halala.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.Globals
import bassamalim.halala.core.domain.Loans
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.Rules
import bassamalim.halala.core.domain.feedOf
import bassamalim.halala.core.domain.toItem
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.enums.BudgetScope
import bassamalim.halala.core.domain.BudgetState
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.shortDateLabel
import bassamalim.halala.features.recurring.RecurringDomain
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val domain: HomeDomain,
    private val navigator: Navigator
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        domain.observeAccounts(),
        domain.observeTransactions(),
        domain.observeLoans(),
        domain.observeRecurring(),
        domain.observeOverview()
    ) { accounts, transactions, loans, recurring, overview ->
        val wallet = accounts.firstOrNull { it.account.type == AccountType.CASH && !it.account.archived }
        val today = domain.today()

        HomeUiState(
            isLoading = false,
            cashWalletId = wallet?.account?.id,
            cashBalance = Money.format(wallet?.balanceMinor ?: 0, wallet?.account?.currency ?: Globals.PRIMARY_CURRENCY, decimals = false),
            bankBalance = Money.format(HomeDomain.bankTotal(accounts), Globals.PRIMARY_CURRENCY, decimals = false),
            bankAccountCount = HomeDomain.bankAccounts(accounts).size,
            reviewCount = Rules.clusters(transactions).size,
            recent = feedOf(transactions)
                .take(HomeDomain.RECENT_COUNT)
                .map { it.toItem(domain.zone(), today) },
            hasLoans = loans.isNotEmpty(),
            owedToYou = Money.format(Loans.owed(loans, Globals.PRIMARY_CURRENCY).first, Globals.PRIMARY_CURRENCY, decimals = false),
            youOwe = Money.format(Loans.owed(loans, Globals.PRIMARY_CURRENCY).second, Globals.PRIMARY_CURRENCY, decimals = false),
            comingUp = RecurringDomain.upcoming(recurring)
                .filter { !it.nextDue!!.isAfter(today.plusDays(RecurringDomain.SOON_DAYS)) }
                .take(HomeDomain.COMING_UP_COUNT)
                .map {
                    ComingUp(
                        name = it.series.name,
                        due = shortDateLabel(it.nextDue!!, today),
                        amount = Money.format(it.raisedTo ?: it.series.amountMinor, it.series.currency, decimals = false)
                    )
                },
            balance = overview.statuses.firstOrNull { it.budget.scope == BudgetScope.TOTAL && it.budget.currency == overview.currency }
                ?.let { status ->
                    BalanceInfo(
                        spent = Money.format(status.spentMinor, overview.currency, decimals = false),
                        limit = Money.format(status.limitMinor, overview.currency, decimals = false),
                        over = (-status.leftMinor).takeIf { it > 0 }?.let { Money.format(it, overview.currency, decimals = false) },
                        progress = BudgetState.progress(status.spentMinor, status.limitMinor),
                        state = if (status.state == BudgetState.OK && status.paceAhead) BudgetState.WARN else status.state,
                        daysLeft = overview.cycle.daysLeft(today).toInt()
                    )
                }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    fun onReviewClick() = navigator.navigate(Screen.Review)

    fun onSettingsClick() = navigator.navigate(Screen.Settings)

    fun onCashClick() {
        uiState.value.cashWalletId?.let { navigator.navigate(Screen.ReconcileCash(it)) }
    }

    fun onAccountsClick() = navigator.navigate(Screen.Accounts)

    fun onPeopleClick() = navigator.navigate(Screen.People)

    fun onComingUpClick() = navigator.navigate(Screen.Recurring)

    fun onTransactionClick(id: Long) = navigator.navigate(Screen.Transaction(id))
}
