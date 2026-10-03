package bassamalim.halala.features.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.backup.BackupOutcome
import bassamalim.halala.core.backup.Backups
import bassamalim.halala.core.data.repositories.PreferencesRepository
import bassamalim.halala.core.models.BackupEvery
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.utils.dateLabel
import bassamalim.halala.core.utils.timeLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

enum class PassphraseProblem { TOO_SHORT, MISMATCH, HINT_IS_PASSPHRASE }

data class PassphraseForm(
    val first: String = "",
    val second: String = "",
    val hint: String = "",
    val problem: PassphraseProblem? = null
)

data class BackupUiState(
    val isLoading: Boolean = true,
    val hasPassphrase: Boolean = false,
    val hasRecoveryKey: Boolean = false,
    /** A recovery key just made, shown this once to be written down. */
    val recoveryCode: String? = null,
    val confirmingNewRecovery: Boolean = false,
    /** Setting or changing the passphrase: the form is open. */
    val form: PassphraseForm? = null,
    val folderName: String? = null,
    val hasFolder: Boolean = false,
    val every: BackupEvery = BackupEvery.OFF,
    val keep: Int = 10,
    /** "Fri 2 Oct, 14:05" */
    val lastLabel: String? = null,
    val working: Boolean = false,
    val confirmingOff: Boolean = false
)

sealed interface BackupEvent {
    data class Finished(val outcome: BackupOutcome) : BackupEvent
    data object PassphraseSet : BackupEvent
}

/** Encrypted backups: the passphrase, the folder, how often, how many to keep, and one now. */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backups: Backups,
    private val preferences: PreferencesRepository,
    private val navigator: Navigator,
    private val clock: Clock
) : ViewModel() {

    private data class Local(
        val hasPassphrase: Boolean,
        val hasRecoveryKey: Boolean = false,
        val recoveryCode: String? = null,
        val confirmingNewRecovery: Boolean = false,
        val form: PassphraseForm? = null,
        val working: Boolean = false,
        val confirmingOff: Boolean = false
    )

    private val local = MutableStateFlow(Local(hasPassphrase = backups.hasPassphrase()))

    init {
        viewModelScope.launch {
            val has = backups.hasRecoveryKey()
            local.update { it.copy(hasRecoveryKey = has) }
        }
    }
    private val events = Channel<BackupEvent>()
    val eventFlow = events.receiveAsFlow()

    private val settings = preferences.observeBackupSettings().map { it to it.folder?.let { folder -> backups.folderName(folder) } }

    val uiState: StateFlow<BackupUiState> = combine(settings, local) { (settings, folderName), local ->
        val today = LocalDate.now(clock)
        BackupUiState(
            isLoading = false,
            hasPassphrase = local.hasPassphrase,
            hasRecoveryKey = local.hasRecoveryKey,
            recoveryCode = local.recoveryCode,
            confirmingNewRecovery = local.confirmingNewRecovery,
            form = local.form,
            folderName = folderName,
            hasFolder = settings.folder != null,
            every = settings.every,
            keep = settings.keep,
            lastLabel = settings.lastAt?.atZone(clock.zone)?.let { "${dateLabel(it.toLocalDate(), today)}, ${timeLabel(it.toLocalTime())}" },
            working = local.working,
            confirmingOff = local.confirmingOff
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    fun onBackClick() = navigator.popBackStack()

    fun onPassphraseClick() = local.update { it.copy(form = PassphraseForm()) }
    fun onFirstChange(text: String) = local.update { it.copy(form = it.form?.copy(first = text, problem = null)) }
    fun onSecondChange(text: String) = local.update { it.copy(form = it.form?.copy(second = text, problem = null)) }
    fun onHintChange(text: String) = local.update { it.copy(form = it.form?.copy(hint = text.take(MAX_HINT), problem = null)) }
    fun onFormDismiss() = local.update { it.copy(form = null) }

    fun onPassphraseSave() {
        val form = local.value.form ?: return
        val problem = when {
            form.first.length < MIN_LENGTH -> PassphraseProblem.TOO_SHORT
            form.first != form.second -> PassphraseProblem.MISMATCH
            form.hint.isNotBlank() && form.first.contains(form.hint.trim(), ignoreCase = true) -> PassphraseProblem.HINT_IS_PASSPHRASE
            else -> null
        }
        if (problem != null) return local.update { it.copy(form = form.copy(problem = problem)) }
        local.update { it.copy(working = true) }
        viewModelScope.launch {
            val code = backups.setPassphrase(form.first.toCharArray(), form.hint)
            local.update { it.copy(hasPassphrase = true, hasRecoveryKey = true, recoveryCode = code, form = null, working = false) }
            events.send(BackupEvent.PassphraseSet)
        }
    }

    /** A recovery key for the passphrase already set; replacing one asks first. */
    fun onRecoveryClick() {
        if (local.value.hasRecoveryKey) return local.update { it.copy(confirmingNewRecovery = true) }
        makeRecoveryKey()
    }

    fun onNewRecoveryConfirm() {
        local.update { it.copy(confirmingNewRecovery = false) }
        makeRecoveryKey()
    }

    fun onNewRecoveryDismiss() = local.update { it.copy(confirmingNewRecovery = false) }

    /** Written down: the key is dropped and never shown again. */
    fun onRecoveryDone() = local.update { it.copy(recoveryCode = null) }

    private fun makeRecoveryKey() {
        if (local.value.working) return
        local.update { it.copy(working = true) }
        viewModelScope.launch {
            val code = backups.newRecoveryKey()
            local.update { it.copy(working = false, hasRecoveryKey = code != null || it.hasRecoveryKey, recoveryCode = code) }
        }
    }

    fun onFolderPicked(uri: String) {
        viewModelScope.launch { preferences.setBackupFolder(uri) }
    }

    fun onEveryClick(every: BackupEvery) {
        viewModelScope.launch {
            preferences.setBackupEvery(every)
            backups.schedule(every)
        }
    }

    fun onKeepClick(keep: Int) {
        viewModelScope.launch { preferences.setBackupKeep(keep) }
    }

    fun onBackUpNowClick() {
        if (local.value.working) return
        local.update { it.copy(working = true) }
        viewModelScope.launch {
            val outcome = backups.backUpNow()
            local.update { it.copy(working = false) }
            events.send(BackupEvent.Finished(outcome))
        }
    }

    fun onTurnOffClick() = local.update { it.copy(confirmingOff = true) }
    fun onTurnOffDismiss() = local.update { it.copy(confirmingOff = false) }
    fun onTurnOffConfirm() {
        viewModelScope.launch {
            backups.clearPassphrase()
            local.update { it.copy(hasPassphrase = false, hasRecoveryKey = false, confirmingOff = false) }
        }
    }

    companion object {
        const val MIN_LENGTH = 8
        /** 50 characters is under the file's 200 bytes in any script. */
        const val MAX_HINT = 50
        val KEEPS = listOf(5, 10, 20)
    }
}
