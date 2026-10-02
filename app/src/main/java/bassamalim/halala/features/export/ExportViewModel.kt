package bassamalim.halala.features.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.BuildConfig
import bassamalim.halala.core.di.IoDispatcher
import bassamalim.halala.core.export.LedgerSnapshot
import bassamalim.halala.core.nav.Navigator
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
    @param:IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    private val state = MutableStateFlow(ExportUiState())
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

    /** The screen owns the file; this reads and checks it, then asks before replacing anything. */
    fun onRestorePicked(read: () -> ByteArray?) {
        state.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            val snapshot = withContext(io) { runCatching { domain.read(checkNotNull(read())) }.getOrNull() }
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
