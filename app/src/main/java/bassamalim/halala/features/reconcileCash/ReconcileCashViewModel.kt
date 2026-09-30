package bassamalim.halala.features.reconcileCash

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import bassamalim.halala.core.domain.CashGap
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReconcileCashViewModel @Inject constructor(
    private val domain: ReconcileCashDomain,
    private val navigator: Navigator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val accountId = savedStateHandle.toRoute<Screen.ReconcileCash>().accountId

    private val counted = MutableStateFlow("")
    private val showInvalid = MutableStateFlow(false)

    private var recordedMinor: Long? = null
    private var currency: String = ""

    val uiState: StateFlow<ReconcileCashUiState> = combine(
        domain.observeAccount(accountId),
        counted,
        showInvalid
    ) { account, counted, showInvalid ->
        if (account == null) return@combine ReconcileCashUiState()

        val currency = account.account.currency
        recordedMinor = account.balanceMinor
        this.currency = currency

        val countedMinor = Money.parse(counted, currency)

        ReconcileCashUiState(
            isLoading = false,
            recorded = Money.format(account.balanceMinor, currency),
            currency = currency,
            counted = counted,
            outcome = countedMinor?.let {
                when (val gap = CashGap.of(account.balanceMinor, it)) {
                    CashGap.None -> Outcome.Matches
                    is CashGap.Spent -> Outcome.Spent(Money.format(gap.amountMinor, currency))
                    is CashGap.Found -> Outcome.Found(Money.format(gap.amountMinor, currency))
                }
            },
            isInvalid = showInvalid && countedMinor == null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReconcileCashUiState()
    )

    fun onBackClick() = navigator.popBackStack()

    fun onCountedChange(value: String) {
        counted.value = value
        showInvalid.value = false
    }

    fun onSaveClick() {
        val recorded = recordedMinor ?: return
        val countedMinor = Money.parse(counted.value, currency)
            ?: return showInvalid.update { true }

        viewModelScope.launch {
            domain.reconcile(accountId, recorded, countedMinor)
            navigator.popBackStack()
        }
    }
}
