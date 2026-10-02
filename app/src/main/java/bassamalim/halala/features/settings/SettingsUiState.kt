package bassamalim.halala.features.settings

import bassamalim.halala.core.models.ReviewSchedule

data class SettingsUiState(
    val accountCount: Int = 0,
    val bankCount: Int = 0,
    /** How long you may be away before the lock asks again, in whole minutes. */
    val lockMinutes: Int = 1,
    val version: String = "",
    val reminder: ReviewSchedule = ReviewSchedule(),
    /** The reminder's time as it reads: "20:00". */
    val reminderTime: String = "",
    val isEditingReminder: Boolean = false,
    val isPickingReminderTime: Boolean = false
)
