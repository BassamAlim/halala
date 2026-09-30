package bassamalim.halala.features.wealth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.Globals
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class WealthUiState(
    /** Every active SAR account, the wallet included, summary style. */
    val accountsTotal: String = "",
    val accountCount: Int = 0
)

@HiltViewModel
class WealthViewModel @Inject constructor(
    accountsRepository: AccountsRepository,
    private val navigator: Navigator
) : ViewModel() {

    val uiState: StateFlow<WealthUiState> = accountsRepository.observeAll()
        .map { accounts ->
            val active = accounts.filter { !it.account.archived }
            val sar = active.filter { it.account.currency == Globals.PRIMARY_CURRENCY }

            WealthUiState(
                accountsTotal = Money.format(Money.sum(sar.map { it.balanceMinor }), Globals.PRIMARY_CURRENCY, decimals = false),
                accountCount = active.size
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WealthUiState()
        )

    fun onAccountsClick() = navigator.navigate(Screen.Accounts)
}
