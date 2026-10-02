package bassamalim.halala.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.BuildConfig
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    domain: SettingsDomain,
    private val navigator: Navigator
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        domain.observeAccounts(),
        domain.observeLockTimeoutSeconds()
    ) { accounts, lockSeconds ->
        val active = accounts.filter { !it.account.archived }

        SettingsUiState(
            accountCount = active.size,
            bankCount = active.mapNotNull { it.account.institutionId }.distinct().size,
            lockMinutes = (lockSeconds / 60).coerceAtLeast(1),
            version = BuildConfig.VERSION_NAME
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun onBackClick() = navigator.popBackStack()

    fun onAccountsClick() = navigator.navigate(Screen.Accounts)

    fun onExportClick() = navigator.navigate(Screen.Export)

    fun onCategoriesClick() = navigator.navigate(Screen.Categories)

    fun onRulesClick() = navigator.navigate(Screen.Rules)

    fun onMessagesClick() = navigator.navigate(Screen.Onboarding(fromSettings = true))
}
