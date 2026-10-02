package bassamalim.halala.core.data.repositories

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import bassamalim.halala.core.domain.DigestKind
import bassamalim.halala.core.models.BackupEvery
import bassamalim.halala.core.models.BackupSettings
import bassamalim.halala.core.models.ReminderMode
import bassamalim.halala.core.models.ReviewSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
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

    /** Whether the first-run SMS setup was finished or skipped. */
    fun observeOnboarded(): Flow<Boolean> = dataStore.data.map { it[ONBOARDED] ?: false }

    suspend fun setOnboarded() {
        dataStore.edit { it[ONBOARDED] = true }
    }

    fun observeReviewSchedule(): Flow<ReviewSchedule> = dataStore.data.map { preferences ->
        val default = ReviewSchedule()
        ReviewSchedule(
            mode = ReminderMode.entries.firstOrNull { it.name == preferences[REVIEW_MODE] } ?: default.mode,
            day = preferences[REVIEW_DAY]?.let { DayOfWeek.entries.getOrNull(it - 1) } ?: default.day,
            time = preferences[REVIEW_MINUTE]?.let { LocalTime.ofSecondOfDay(it.coerceIn(0, 1439) * 60L) } ?: default.time
        )
    }

    suspend fun setReviewSchedule(schedule: ReviewSchedule) {
        dataStore.edit {
            it[REVIEW_MODE] = schedule.mode.name
            it[REVIEW_DAY] = schedule.day.value
            it[REVIEW_MINUTE] = schedule.time.toSecondOfDay() / 60
        }
    }

    /** Which digests to be told about: weekly, monthly, yearly. All off until you choose. */
    fun observeDigests(): Flow<Set<DigestKind>> = dataStore.data.map { preferences ->
        DigestKind.entries.filter { preferences[digestKey(it)] ?: false }.toSet()
    }

    suspend fun setDigest(kind: DigestKind, on: Boolean) {
        dataStore.edit { it[digestKey(kind)] = on }
    }

    /** Where scheduled backups go and how often: never money, never the passphrase. */
    fun observeBackupSettings(): Flow<BackupSettings> = dataStore.data.map { preferences ->
        BackupSettings(
            folder = preferences[BACKUP_FOLDER],
            every = BackupEvery.entries.firstOrNull { it.name == preferences[BACKUP_EVERY] } ?: BackupEvery.OFF,
            keep = preferences[BACKUP_KEEP] ?: BackupSettings.DEFAULT_KEEP,
            lastAt = preferences[BACKUP_LAST_AT]?.let(Instant::ofEpochMilli)
        )
    }

    suspend fun setBackupFolder(uri: String?) {
        dataStore.edit { if (uri == null) it.remove(BACKUP_FOLDER) else it[BACKUP_FOLDER] = uri }
    }

    suspend fun setBackupEvery(every: BackupEvery) {
        dataStore.edit { it[BACKUP_EVERY] = every.name }
    }

    suspend fun setBackupKeep(keep: Int) {
        dataStore.edit { it[BACKUP_KEEP] = keep.coerceAtLeast(1) }
    }

    suspend fun setBackedUp(at: Instant) {
        dataStore.edit { it[BACKUP_LAST_AT] = at.toEpochMilli() }
    }

    /** Whether location was asked for once, after onboarding (the map asks again on its own). */
    fun observeLocationAsked(): Flow<Boolean> = dataStore.data.map { it[LOCATION_ASKED] ?: false }

    suspend fun setLocationAsked() {
        dataStore.edit { it[LOCATION_ASKED] = true }
    }

    /** Tag suggestions you said no to, by their keys (a currency and a day: nothing about money). */
    fun observeDismissedTagSuggestions(): Flow<Set<String>> = dataStore.data.map { it[TAG_DISMISSED].orEmpty() }

    suspend fun dismissTagSuggestion(key: String) {
        dataStore.edit { it[TAG_DISMISSED] = it[TAG_DISMISSED].orEmpty() + key }
    }

    /**
     * Pairs of people the AI read as one, and pairs you said aren't, by `People.pairKey` (their
     * uids, never a name).
     */
    fun observePeopleSame(): Flow<Set<String>> = dataStore.data.map { it[PEOPLE_SAME].orEmpty() }

    fun observePeopleDismissed(): Flow<Set<String>> = dataStore.data.map { it[PEOPLE_DISMISSED].orEmpty() }

    suspend fun dismissPeoplePair(key: String) {
        dataStore.edit { it[PEOPLE_DISMISSED] = it[PEOPLE_DISMISSED].orEmpty() + key }
    }

    /** The people (uids) the AI has already been asked about. */
    suspend fun peopleAsked(): Set<String> = dataStore.data.first()[PEOPLE_ASKED].orEmpty()

    /** [asked] is everyone the AI has now seen, [same] the pairs it found among them. */
    suspend fun recordPeopleMatches(asked: Set<String>, same: Set<String>) {
        dataStore.edit {
            it[PEOPLE_ASKED] = asked
            it[PEOPLE_SAME] = it[PEOPLE_SAME].orEmpty() + same
        }
    }

    private fun digestKey(kind: DigestKind) = booleanPreferencesKey("digest_${kind.name.lowercase()}")

    companion object {
        private val REVIEW_MODE = stringPreferencesKey("review_reminder_mode")
        private val REVIEW_DAY = intPreferencesKey("review_reminder_day")
        private val REVIEW_MINUTE = intPreferencesKey("review_reminder_minute")
        private val ONBOARDED = booleanPreferencesKey("onboarded")
        private val LOCK_TIMEOUT_SECONDS = intPreferencesKey("lock_timeout_seconds")
        private val LOCATION_ASKED = booleanPreferencesKey("location_asked")
        private val TAG_DISMISSED = stringSetPreferencesKey("tag_suggestions_dismissed")
        private val PEOPLE_SAME = stringSetPreferencesKey("people_same")
        private val PEOPLE_DISMISSED = stringSetPreferencesKey("people_merge_dismissed")
        private val PEOPLE_ASKED = stringSetPreferencesKey("people_asked")
        private val BACKUP_FOLDER = stringPreferencesKey("backup_folder")
        private val BACKUP_EVERY = stringPreferencesKey("backup_every")
        private val BACKUP_KEEP = intPreferencesKey("backup_keep")
        private val BACKUP_LAST_AT = longPreferencesKey("backup_last_at")

        /** The spec's default: a minute in the background asks again. */
        const val DEFAULT_LOCK_TIMEOUT_SECONDS = 60
    }
}
