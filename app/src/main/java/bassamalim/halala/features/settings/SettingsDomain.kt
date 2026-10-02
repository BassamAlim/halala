package bassamalim.halala.features.settings

import bassamalim.halala.core.ai.AiScheduler
import bassamalim.halala.core.ai.ApiKeys
import bassamalim.halala.core.ai.IdentifyProblem
import bassamalim.halala.core.data.dataSources.room.relations.AccountWithBalance
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.data.repositories.PreferencesRepository
import bassamalim.halala.core.di.IoDispatcher
import bassamalim.halala.core.models.ReviewSchedule
import bassamalim.halala.core.reminders.ReviewReminders
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SettingsDomain @Inject constructor(
    private val accountsRepository: AccountsRepository,
    private val preferencesRepository: PreferencesRepository,
    private val classificationRepository: ClassificationRepository,
    private val reviewReminders: ReviewReminders,
    private val apiKeys: ApiKeys,
    private val ai: AiScheduler,
    @param:IoDispatcher private val io: CoroutineDispatcher
) {

    fun observeAiEnabled(): Flow<Boolean> = preferencesRepository.observeAiEnabled()

    /** Why identifying last stopped short; none when it last went through. */
    fun observeAiProblem(): Flow<IdentifyProblem?> = preferencesRepository.observeAiProblem()
        .map { name -> IdentifyProblem.entries.firstOrNull { it.name == name } }

    /** Merchants with spending nothing has filed that nobody has identified yet. */
    fun observeWaiting(): Flow<Int> = classificationRepository.observeToIdentifyCount()

    suspend fun hasGroqKey(): Boolean = withContext(io) { apiKeys.hasGroq() }

    /** Keeps the key, encrypted, and starts identifying if it is on. */
    suspend fun saveGroqKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return
        withContext(io) { apiKeys.setGroq(trimmed) }
        preferencesRepository.setAiProblem(null)
        ai.request()
    }

    /** Forgets the key, and turns identifying off: it can't run without one. */
    suspend fun forgetGroqKey() {
        withContext(io) { apiKeys.setGroq(null) }
        preferencesRepository.setAiEnabled(false)
        preferencesRepository.setAiProblem(null)
    }

    suspend fun setAiEnabled(enabled: Boolean) {
        preferencesRepository.setAiEnabled(enabled)
        if (enabled) ai.request()
    }

    suspend fun identifyNow() = ai.request()


    fun observeReviewSchedule(): Flow<ReviewSchedule> = preferencesRepository.observeReviewSchedule()

    /** Remembers the schedule and sets the reminder to it. */
    suspend fun setReviewSchedule(schedule: ReviewSchedule) {
        preferencesRepository.setReviewSchedule(schedule)
        reviewReminders.schedule(schedule)
    }


    fun observeAccounts(): Flow<List<AccountWithBalance>> = accountsRepository.observeAll()

    fun observeLockTimeoutSeconds(): Flow<Int> = preferencesRepository.observeLockTimeoutSeconds()
}
