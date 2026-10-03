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
    val hideAmounts: Boolean = false,
    /** How long Halala can be in the background before it asks who you are again. */
    val lockTimeoutSeconds: Int = 60,
    val isEditingLock: Boolean = false
) {
    companion object {
        /** The lock's choices: at once, a minute (the default), five and fifteen. */
        val LOCK_TIMEOUTS = listOf(0, 60, 300, 900)
    }
}
