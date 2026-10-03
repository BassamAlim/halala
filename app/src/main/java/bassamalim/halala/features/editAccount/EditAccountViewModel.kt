package bassamalim.halala.features.editAccount

import bassamalim.halala.core.utils.OneAtATime
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.enums.AccountType
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
class EditAccountViewModel @Inject constructor(
    private val domain: EditAccountDomain,
    private val navigator: Navigator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val saving = OneAtATime()

    private val id = savedStateHandle.toRoute<Screen.EditAccount>().id

    private val local = MutableStateFlow(Local(isLoading = id != 0L))

    val uiState: StateFlow<EditAccountUiState> = combine(
        domain.observeInstitutions(),
        local
    ) { institutions, local ->
        EditAccountUiState(
            isLoading = local.isLoading,
            isNew = id == 0L,
            isCash = local.form.type == AccountType.CASH,
            isArchived = local.isArchived,
            banks = institutions.map { BankOption(it.id, it.name) },
            form = local.form,
            currencyChoice = local.form.currency.takeIf { it in EditAccountUiState.CURRENCY_CHOICES && !local.typingCurrency },
            currencyLocked = local.currencyLocked,
            problems = local.problems
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EditAccountUiState()
    )

    init {
        if (id != 0L) viewModelScope.launch {
            val loaded = domain.load(id) ?: return@launch navigator.popBackStack()
            val account = loaded.account

            local.update {
                it.copy(
                    isLoading = false,
                    isArchived = account.archived,
                    currencyLocked = loaded.transactionCount > 0,
                    typingCurrency = account.currency !in EditAccountUiState.CURRENCY_CHOICES,
                    form = AccountForm(
                        institutionId = account.institutionId,
                        type = account.type,
                        name = account.nickname,
                        last4 = account.last4.orEmpty(),
                        currency = account.currency,
                        openingBalance =
                            if (account.openingBalanceMinor == 0L) ""
                            else Money.input(account.openingBalanceMinor, account.currency)
                    )
                )
            }
        }
    }

    fun onBackClick() = navigator.popBackStack()

    fun onBankClick(institutionId: Long) = edit { it.copy(institutionId = institutionId) }

    fun onTypeClick(type: AccountType) = edit { it.copy(type = type) }

    fun onNameChange(name: String) = edit { it.copy(name = name) }

    /** Digits only, four at most: anything else can't be what an SMS quotes. */
    fun onLast4Change(value: String) = edit { it.copy(last4 = value.filter(Char::isDigit).take(4)) }

    fun onCurrencyClick(currency: String?) {
        local.update {
            if (currency == null) it.copy(typingCurrency = true, form = it.form.copy(currency = ""))
            else it.copy(typingCurrency = false, form = it.form.copy(currency = currency))
        }
    }

    fun onCurrencyChange(value: String) =
        edit { it.copy(currency = value.filter(Char::isLetter).take(3).uppercase()) }

    fun onOpeningBalanceChange(value: String) = edit { it.copy(openingBalance = value) }

    fun onBalanceNowChange(value: String) = edit { it.copy(balanceNow = value) }

    fun onSaveClick() {
        saving.launch(viewModelScope) {
            when (val result = domain.save(id, local.value.form)) {
                AccountSave.Saved -> navigator.popBackStack()
                is AccountSave.Invalid -> local.update { it.copy(problems = result.problems) }
            }
            local.value.problems.isEmpty()
        }
    }

    /** Archiving is undone by the same button, so it asks nothing. */
    fun onArchiveClick() {
        saving.launch(viewModelScope) {
            domain.setArchived(id, !local.value.isArchived)
            navigator.popBackStack()
            true
        }
    }

    fun onCountCashClick() = navigator.navigate(Screen.ReconcileCash(id))

    /** An edit clears the problems it might have fixed; Save checks again. */
    private fun edit(change: (AccountForm) -> AccountForm) =
        local.update { it.copy(form = change(it.form), problems = emptySet()) }

    private data class Local(
        val isLoading: Boolean = false,
        val isArchived: Boolean = false,
        val currencyLocked: Boolean = false,
        val typingCurrency: Boolean = false,
        val form: AccountForm = AccountForm(),
        val problems: Set<AccountProblem> = emptySet()
    )
}
