package bassamalim.halala.core.models

import java.time.Instant

enum class BackupEvery { OFF, DAILY, WEEKLY }

/** Scheduled backups: the folder (a tree URI you granted), how often, how many to keep, and the last one. */
data class BackupSettings(
    val folder: String? = null,
    val every: BackupEvery = BackupEvery.OFF,
    val keep: Int = DEFAULT_KEEP,
    val lastAt: Instant? = null
) {
    companion object {
        /** The spec's: the last ten. */
        const val DEFAULT_KEEP = 10
    }
}
