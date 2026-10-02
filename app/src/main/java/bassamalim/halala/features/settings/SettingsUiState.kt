package bassamalim.halala.features.settings

import bassamalim.halala.core.domain.DigestKind
import bassamalim.halala.core.models.ReviewSchedule

data class SettingsUiState(
    val accountCount: Int = 0,
    val bankCount: Int = 0,
    val version: String = "",
    val reminder: ReviewSchedule = ReviewSchedule(),
    /** The reminder's time as it reads: "20:00". */
    val reminderTime: String = "",
    val isEditingReminder: Boolean = false,
    val isPickingReminderTime: Boolean = false,
    /** The digests you are told about. */
    val digests: Set<DigestKind> = emptySet(),
    val isEditingDigests: Boolean = false,
    /** Every amount reads as dots until you show them again, which asks who you are. */
    val hideAmounts: Boolean = false
)
