package bassamalim.halala.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.BuildConfig
import bassamalim.halala.core.models.ReminderMode
import bassamalim.halala.core.models.ReviewSchedule
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.timeLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val domain: SettingsDomain,
    private val navigator: Navigator
) : ViewModel() {

    private val editingReminder = MutableStateFlow(false)
    private val pickingReminderTime = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        domain.observeAccounts(),
        domain.observeLockTimeoutSeconds(),
        domain.observeReviewSchedule(),
        editingReminder,
        pickingReminderTime
    ) { accounts, lockSeconds, reminder, editingReminder, pickingReminderTime ->
        val active = accounts.filter { !it.account.archived }

        SettingsUiState(
            accountCount = active.size,
            bankCount = active.mapNotNull { it.account.institutionId }.distinct().size,
            lockMinutes = (lockSeconds / 60).coerceAtLeast(1),
            version = BuildConfig.VERSION_NAME,
            reminder = reminder,
            reminderTime = timeLabel(reminder.time),
            isEditingReminder = editingReminder,
            isPickingReminderTime = pickingReminderTime
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun onReminderClick() = editingReminder.update { true }

    fun onReminderDismiss() = editingReminder.update { false }

    fun onReminderModePick(mode: ReminderMode) = setReminder { it.copy(mode = mode) }

    fun onReminderDayPick(day: DayOfWeek) = setReminder { it.copy(day = day) }

    fun onReminderTimeClick() = pickingReminderTime.update { true }

    fun onReminderTimeDismiss() = pickingReminderTime.update { false }

    fun onReminderTimePicked(time: LocalTime) {
        pickingReminderTime.update { false }
        setReminder { it.copy(time = time) }
    }

    private fun setReminder(change: (ReviewSchedule) -> ReviewSchedule) {
        val changed = change(uiState.value.reminder)
        viewModelScope.launch { domain.setReviewSchedule(changed) }
    }

    fun onBackClick() = navigator.popBackStack()

    fun onAccountsClick() = navigator.navigate(Screen.Accounts)

    fun onExportClick() = navigator.navigate(Screen.Export)

    fun onCategoriesClick() = navigator.navigate(Screen.Categories)

    fun onHistoryClick() = navigator.navigate(Screen.History)

    fun onRulesClick() = navigator.navigate(Screen.Rules)

    fun onMerchantsClick() = navigator.navigate(Screen.Merchants)

    fun onMessagesClick() = navigator.navigate(Screen.Onboarding(fromSettings = true))
}
