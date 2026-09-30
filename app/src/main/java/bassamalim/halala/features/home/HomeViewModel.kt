package bassamalim.halala.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.Globals
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.feedOf
import bassamalim.halala.core.domain.toItem
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
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
        domain.observeTransactions()
    ) { accounts, transactions ->
        val wallet = accounts.firstOrNull { it.account.type == AccountType.CASH && !it.account.archived }
        val today = domain.today()

        HomeUiState(
            isLoading = false,
            cashWalletId = wallet?.account?.id,
            cashBalance = Money.format(wallet?.balanceMinor ?: 0, wallet?.account?.currency ?: Globals.PRIMARY_CURRENCY, decimals = false),
            bankBalance = Money.format(HomeDomain.bankTotal(accounts), Globals.PRIMARY_CURRENCY, decimals = false),
            bankAccountCount = HomeDomain.bankAccounts(accounts).size,
            recent = feedOf(transactions)
                .take(HomeDomain.RECENT_COUNT)
                .map { it.toItem(domain.zone(), today) }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    fun onSettingsClick() = navigator.navigate(Screen.Settings)

    fun onCashClick() {
        uiState.value.cashWalletId?.let { navigator.navigate(Screen.ReconcileCash(it)) }
    }

    fun onAccountsClick() = navigator.navigate(Screen.Accounts)

    fun onTransactionClick(id: Long) = navigator.navigate(Screen.Transaction(id))
}
