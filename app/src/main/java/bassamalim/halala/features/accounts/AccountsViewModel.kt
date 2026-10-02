package bassamalim.halala.features.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.Globals
import bassamalim.halala.core.data.dataSources.room.relations.AccountWithBalance
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.initialOf
import bassamalim.halala.core.utils.maskedLast4
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AccountsViewModel @Inject constructor(
    domain: AccountsDomain,
    private val navigator: Navigator
) : ViewModel() {

    val uiState: StateFlow<AccountsUiState> = domain.observeAccounts()
        .map { accounts ->
            val (archived, active) = accounts.filter { it.account.type.listed }.partition { it.account.archived }
            AccountsUiState(
                isLoading = false,
                active = active.map { it.toRow() },
                archived = archived.map { it.toRow() }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AccountsUiState()
        )

    fun onBackClick() = navigator.popBackStack()

    fun onAddClick() = navigator.navigate(Screen.EditAccount())

    fun onAccountClick(id: Long) = navigator.navigate(Screen.EditAccount(id))

    private fun AccountWithBalance.toRow(): AccountRow {
        val formatted = Money.format(balanceMinor, account.currency)

        return AccountRow(
            id = account.id,
            name = account.nickname,
            institution = institutionName,
            last4 = account.last4?.let(::maskedLast4),
            type = account.type,
            balance = if (account.currency == Globals.PRIMARY_CURRENCY) formatted else "$formatted ${account.currency}",
            initial = initialOf(institutionName ?: account.nickname)
        )
    }
}
