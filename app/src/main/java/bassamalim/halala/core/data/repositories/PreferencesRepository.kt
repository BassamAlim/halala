package bassamalim.halala.core.data.repositories

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import bassamalim.halala.core.models.ReminderMode
import bassamalim.halala.core.models.ReviewSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
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

    /** Whether merchants are identified by AI (Groq). Off until you turn it on. */
    fun observeAiEnabled(): Flow<Boolean> = dataStore.data.map { it[AI_ENABLED] ?: false }

    suspend fun setAiEnabled(enabled: Boolean) {
        dataStore.edit { it[AI_ENABLED] = enabled }
    }

    /** Why identifying last stopped short ("KEY", "UNREACHABLE", …); null when it last went through. */
    fun observeAiProblem(): Flow<String?> = dataStore.data.map { it[AI_PROBLEM] }

    suspend fun setAiProblem(problem: String?) {
        dataStore.edit { if (problem == null) it.remove(AI_PROBLEM) else it[AI_PROBLEM] = problem }
    }

    companion object {
        private val AI_ENABLED = booleanPreferencesKey("ai_enabled")
        private val AI_PROBLEM = stringPreferencesKey("ai_problem")
        private val REVIEW_MODE = stringPreferencesKey("review_reminder_mode")
        private val REVIEW_DAY = intPreferencesKey("review_reminder_day")
        private val REVIEW_MINUTE = intPreferencesKey("review_reminder_minute")
        private val ONBOARDED = booleanPreferencesKey("onboarded")
        private val LOCK_TIMEOUT_SECONDS = intPreferencesKey("lock_timeout_seconds")

        /** The spec's default: a minute in the background asks again. */
        const val DEFAULT_LOCK_TIMEOUT_SECONDS = 60
    }
}
