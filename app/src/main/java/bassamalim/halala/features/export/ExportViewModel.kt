package bassamalim.halala.features.export

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import bassamalim.halala.BuildConfig
import bassamalim.halala.core.di.IoDispatcher
import bassamalim.halala.core.export.LedgerSnapshot
import bassamalim.halala.core.backup.WrongPassphrase
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val domain: ExportDomain,
    private val navigator: Navigator,
    @param:IoDispatcher private val io: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val fromOnboarding = savedStateHandle.toRoute<Screen.Export>().fromOnboarding

    private val state = MutableStateFlow(ExportUiState(restoreOnly = fromOnboarding))
    val uiState: StateFlow<ExportUiState> = state.asStateFlow()

    /** The ledger read from the picked file, held until you confirm or back out. */
    private var pending: LedgerSnapshot? = null

    private val _events = Channel<ExportEvent>()
    val events = _events.receiveAsFlow()

    fun onBackClick() = navigator.popBackStack()

    fun csvFileName() = domain.csvFileName()

    fun jsonFileName() = domain.jsonFileName()

    /** The screen owns the file URI; this owns what goes in it. */
    fun onCsvPicked(write: (ByteArray) -> Boolean) = export(write) { domain.csvZip() }

    fun onJsonPicked(write: (ByteArray) -> Boolean) = export(write) { domain.json(BuildConfig.VERSION_NAME) }

    /** A backup picked for restoring, held while its passphrase is asked for. */
    private var sealed: ByteArray? = null

    fun onBackupsClick() = navigator.navigate(Screen.Backup)

    /** The screen owns the file; this reads and checks it, then asks before replacing anything. */
    fun onRestorePicked(read: () -> ByteArray?) {
        state.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            val bytes = withContext(io) { runCatching { read() }.getOrNull() }
            if (bytes != null && domain.isBackup(bytes)) {
                sealed = bytes
                state.update { it.copy(isWorking = false, passphrase = PassphraseAsk(hint = domain.hintOf(bytes))) }
                return@launch
            }
            offer(withContext(io) { runCatching { domain.read(checkNotNull(bytes)) }.getOrNull() })
        }
    }

    fun onPassphraseChange(text: String) = state.update { it.copy(passphrase = it.passphrase?.copy(text = text, wrong = false)) }

    fun onRecoveryToggle() = state.update {
        it.copy(passphrase = it.passphrase?.let { ask -> ask.copy(text = "", wrong = false, recovery = !ask.recovery) })
    }

    fun onPassphraseDismiss() {
        sealed = null
        state.update { it.copy(passphrase = null) }
    }

    /** Opens the backup (slow: the passphrase is stretched), then offers it like an export. */
    fun onPassphraseSubmit() {
        val bytes = sealed ?: return
        val ask = state.value.passphrase ?: return
        val secret = ask.text.takeIf { it.isNotEmpty() } ?: return
        state.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            val result = withContext(io) { runCatching { domain.readBackup(bytes, secret.toCharArray(), ask.recovery) } }
            if (result.exceptionOrNull() is WrongPassphrase) {
                state.update { it.copy(isWorking = false, passphrase = it.passphrase?.copy(wrong = true)) }
                return@launch
            }
            sealed = null
            state.update { it.copy(passphrase = null) }
            offer(result.getOrNull())
        }
    }

    private suspend fun offer(snapshot: LedgerSnapshot?) {
        pending = snapshot
        state.update {
            it.copy(
                isWorking = false,
                restore = snapshot?.let { ledger ->
                    RestoreSummary(
                        transactions = COUNT.format(Locale.US, ledger.transactions.size),
                        accounts = COUNT.format(Locale.US, ledger.accounts.size)
                    )
                }
            )
        }
        if (snapshot == null) _events.send(ExportEvent.Unreadable)
    }

    fun onRestoreDismiss() {
        pending = null
        state.update { it.copy(restore = null) }
    }

    fun onRestoreConfirm() {
        val snapshot = pending ?: return
        pending = null
        state.update { it.copy(restore = null, isWorking = true) }
        viewModelScope.launch {
            val succeeded = withContext(io) { runCatching { domain.restore(snapshot) }.isSuccess }
            state.update { it.copy(isWorking = false) }
            if (succeeded && fromOnboarding) {
                domain.finishOnboarding()
                navigator.navigate(Screen.Main) { popUpTo<Screen.Onboarding> { inclusive = true } }
                return@launch
            }
            _events.send(ExportEvent.Restored(succeeded))
        }
    }

    private fun export(write: (ByteArray) -> Boolean, build: suspend () -> ByteArray) {
        state.update { it.copy(isWorking = true) }

        viewModelScope.launch {
            val succeeded = withContext(io) { runCatching { write(build()) }.getOrDefault(false) }
            state.update { it.copy(isWorking = false) }
            _events.send(ExportEvent.Written(succeeded))
        }
    }

    private companion object {
        const val COUNT = "%,d"
    }
}
