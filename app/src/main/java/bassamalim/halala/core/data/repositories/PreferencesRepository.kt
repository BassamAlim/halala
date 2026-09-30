package bassamalim.halala.core.data.repositories

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings, never money: the ledger is in the encrypted database, and DataStore is not
 * encrypted. The lock itself is always on; only how long you may be away is a preference.
 */
@Singleton
class PreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    fun observeLockTimeoutSeconds(): Flow<Int> =
        dataStore.data.map { it[LOCK_TIMEOUT_SECONDS] ?: DEFAULT_LOCK_TIMEOUT_SECONDS }

    suspend fun setLockTimeoutSeconds(seconds: Int) {
        dataStore.edit { it[LOCK_TIMEOUT_SECONDS] = seconds.coerceAtLeast(0) }
    }

    companion object {
        private val LOCK_TIMEOUT_SECONDS = intPreferencesKey("lock_timeout_seconds")

        /** The spec's default: a minute in the background asks again. */
        const val DEFAULT_LOCK_TIMEOUT_SECONDS = 60
    }
}
