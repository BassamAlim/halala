package bassamalim.halala.features.settings

data class SettingsUiState(
    val accountCount: Int = 0,
    val bankCount: Int = 0,
    /** How long you may be away before the lock asks again, in whole minutes. */
    val lockMinutes: Int = 1,
    val version: String = ""
)
