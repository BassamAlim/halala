package bassamalim.halala.features.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.sms.BankFormats
import bassamalim.halala.core.utils.accountLabel
import bassamalim.halala.core.utils.initialOf
import bassamalim.halala.core.utils.maskedLast4
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val domain: OnboardingDomain,
    private val navigator: Navigator,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Screen.Onboarding>()

    private data class Local(
        val step: OnboardingStep = OnboardingStep.Permission,
        val permissionDenied: Boolean = false,
        /** What you typed, by row key; a row you haven't touched shows its default. */
        val names: Map<String, String> = emptyMap(),
        /** Balances you typed over, by account id. */
        val balances: Map<Long, String> = emptyMap(),
        val showInvalid: Boolean = false
    )

    private val local = MutableStateFlow(Local())
    private var found: List<FoundAccount> = emptyList()
    private var currencies: Map<Long, String> = emptyMap()

    val uiState: StateFlow<OnboardingUiState> = combine(
        domain.observeFound(),
        domain.observeStats(),
        domain.importing,
        domain.observeAccounts(),
        local
    ) { found, stats, importing, accounts, local ->
        this.found = found
        currencies = accounts.associate { it.account.id to it.account.currency }
        val (brokers, toName) = found.partition { it.bank == BROKER }

        OnboardingUiState(
            // With nothing left to name once the reading is done, the history is all there is.
            step = if (local.step == OnboardingStep.Accounts && !importing && toName.isEmpty())
                OnboardingStep.History else local.step,
            isReading = importing,
            permissionDenied = local.permissionDenied,
            offerRestore = !route.fromSettings,
            rows = toName.map { account ->
                FoundRow(
                    key = account.key,
                    bank = account.bank,
                    initial = initialOf(account.bank),
                    digits = account.refs.firstOrNull()?.let(::maskedLast4).orEmpty(),
                    name = local.names[account.key] ?: defaultName(account, toName)
                )
            },
            alsoFound = BROKER.takeIf { brokers.isNotEmpty() },
            messages = COUNT.format(Locale.US, stats.messages),
            transactions = COUNT.format(Locale.US, stats.transactions),
            since = stats.since?.atZone(clock.zone)?.format(MONTH).orEmpty(),
            balances = accounts.map {
                val typed = local.balances[it.account.id]
                BalanceRow(
                    accountId = it.account.id,
                    name = accountLabel(it.institutionName, it.account.nickname),
                    value = typed ?: Money.format(it.balanceMinor, it.account.currency),
                    currency = it.account.currency,
                    isInvalid = local.showInvalid && typed != null &&
                        Money.parseSigned(typed, it.account.currency) == null
                )
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OnboardingUiState()
    )

    /** The screen's answer on SMS access: already held, just granted, or refused. */
    fun onPermission(granted: Boolean) {
        if (!granted) return local.update { it.copy(permissionDenied = true) }

        domain.startImport()
        local.update { it.copy(step = OnboardingStep.Accounts, permissionDenied = false) }
    }

    /**
     * A new phone: restore a backup rather than read the inbox. SMS access was asked for first
     * either way, so new messages still arrive; what came since the backup is read as the app opens.
     */
    fun onRestoreClick() = navigator.navigate(Screen.Export(fromOnboarding = true))

    fun onNameChange(key: String, name: String) = local.update { it.copy(names = it.names + (key to name)) }

    fun onContinueClick() {
        val state = uiState.value
        val names = state.rows.associate { it.key to it.name }
        // The broker needs no name of yours: it is filed under its own.
        val named = found.map { it to if (it.bank == BROKER) BROKER else names[it.key].orEmpty() }

        viewModelScope.launch {
            domain.nameAccounts(named)
            local.update { it.copy(step = OnboardingStep.History) }
        }
    }

    fun onBalanceChange(accountId: Long, value: String) =
        local.update { it.copy(balances = it.balances + (accountId to value), showInvalid = false) }

    /** Done: the balances you corrected become each account's balance from now, then leave. */
    fun onDoneClick() {
        val typed = local.value.balances.filterKeys { it in currencies }
        val parsed = typed.mapValues { (id, text) -> Money.parseSigned(text, currencies.getValue(id)) }
        if (parsed.values.any { it == null }) return local.update { it.copy(showInvalid = true) }

        viewModelScope.launch {
            domain.setBalances(parsed.mapValues { it.value!! })
            onLeaveClick()
        }
    }

    /** Skip and Done both leave: back to Settings when opened from there, else into the app. */
    fun onLeaveClick() {
        viewModelScope.launch {
            domain.finish()
            if (route.fromSettings) navigator.popBackStack()
            else navigator.navigate(Screen.Main) { popUpTo<Screen.Onboarding> { inclusive = true } }
        }
    }

    private companion object {
        val BROKER = BankFormats.AL_RAJHI_CAPITAL.institution
        const val COUNT = "%,d"
        val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)

        /** A bank's only account takes the bank's name, as the board has it; others start blank. */
        fun defaultName(account: FoundAccount, all: List<FoundAccount>): String =
            if (all.count { it.bank == account.bank } == 1) account.bank else ""
    }
}
