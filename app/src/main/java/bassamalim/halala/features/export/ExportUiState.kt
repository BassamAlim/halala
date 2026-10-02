package bassamalim.halala.features.export

data class ExportUiState(
    /** While a file is being written or read; the rows wait for it. */
    val isWorking: Boolean = false,
    /** A file was read and fits: what it holds, to confirm before it replaces the ledger. */
    val restore: RestoreSummary? = null,
    /** A backup was picked: its passphrase is asked for. */
    val passphrase: PassphraseAsk? = null
)

data class PassphraseAsk(val text: String = "", val wrong: Boolean = false)

data class RestoreSummary(
    /** "2,412" */
    val transactions: String,
    val accounts: String
)

/** One-shot outcomes the screen reports and then forgets. */
sealed interface ExportEvent {
    data class Written(val succeeded: Boolean) : ExportEvent
    /** The file picked isn't an export this version can restore. */
    data object Unreadable : ExportEvent
    data class Restored(val succeeded: Boolean) : ExportEvent
}
