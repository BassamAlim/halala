package bassamalim.halala.core.lock

import bassamalim.halala.core.data.repositories.PreferencesRepository
import bassamalim.halala.core.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * When the app should ask who you are. The lock is always on: a cold start always asks (the
 * graph starts on the lock), and after that it asks again once you've been away for the grace
 * period, a minute by default. A quick hop to the bank's app and back doesn't re-prompt.
 */
@Singleton
class LockManager @Inject constructor(
    preferencesRepository: PreferencesRepository,
    @ApplicationScope scope: CoroutineScope
) {

    private val graceMillis = preferencesRepository.observeLockTimeoutSeconds()
        .map { it * 1_000L }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_GRACE_MILLIS)

    /** Monotonic time (not wall clock) so changing the device clock can't skip the grace. */
    private var backgroundedAt: Long? = null
    private var unlocked = false

    fun onUnlocked() {
        unlocked = true
        backgroundedAt = null
    }

    /** Before the first unlock, or since the lock last came back. */
    fun isLocked() = !unlocked

    fun onBackgrounded(now: Long) {
        if (unlocked) backgroundedAt = now
    }

    fun onLocked() {
        unlocked = false
        backgroundedAt = null
    }

    fun shouldLockOnResume(now: Long): Boolean =
        shouldLock(backgroundedAt = backgroundedAt, now = now, graceMillis = graceMillis.value)

    companion object {
        const val DEFAULT_GRACE_MILLIS =
            PreferencesRepository.DEFAULT_LOCK_TIMEOUT_SECONDS * 1_000L
    }
}

/**
 * Pulled out of [LockManager] so the rule itself can be tested without a DataStore. Only an
 * unlocked session that went to the background has a [backgroundedAt]; a session still sitting
 * on the lock has nothing to re-lock.
 */
internal fun shouldLock(backgroundedAt: Long?, now: Long, graceMillis: Long): Boolean {
    val since = backgroundedAt ?: return false
    return now - since >= graceMillis
}
