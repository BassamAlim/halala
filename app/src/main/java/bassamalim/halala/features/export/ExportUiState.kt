package bassamalim.halala.features.export

data class ExportUiState(
    /** While a file is being written; both rows wait for it. */
    val isWorking: Boolean = false
)

/** One-shot outcomes the screen reports and then forgets. */
sealed interface ExportEvent {
    data class Written(val succeeded: Boolean) : ExportEvent
}
