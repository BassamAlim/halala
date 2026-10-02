package bassamalim.halala.features.history

import bassamalim.halala.core.enums.AuditAction
import bassamalim.halala.core.utils.DayLabel

data class HistoryUiState(
    val isLoading: Boolean = true,
    /** Newest first. */
    val changes: List<HistoryItem> = emptyList()
)

data class HistoryItem(
    val id: Long,
    val action: AuditAction,
    val subject: String,
    val detail: String,
    /** How many transactions it re-filed. */
    val transactions: Int,
    val day: DayLabel,
    val undone: Boolean
)
