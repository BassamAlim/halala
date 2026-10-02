package bassamalim.halala.features.settings

import bassamalim.halala.core.data.dataSources.room.relations.AccountWithBalance
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.PreferencesRepository
import bassamalim.halala.core.models.ReviewSchedule
import bassamalim.halala.core.reminders.DueReminders
import bassamalim.halala.core.reminders.ReviewReminders
import bassamalim.halala.core.domain.DigestKind
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SettingsDomain @Inject constructor(
    private val accountsRepository: AccountsRepository,
    private val preferencesRepository: PreferencesRepository,
    private val reviewReminders: ReviewReminders,
    private val dueReminders: DueReminders
) {

    fun observeReviewSchedule(): Flow<ReviewSchedule> = preferencesRepository.observeReviewSchedule()

    /** Remembers the schedule and sets the reminder to it. */
    suspend fun setReviewSchedule(schedule: ReviewSchedule) {
        preferencesRepository.setReviewSchedule(schedule)
        reviewReminders.schedule(schedule)
    }

    fun observeDigests(): Flow<Set<DigestKind>> = preferencesRepository.observeDigests()

    /** Turning a digest on also makes sure the daily check that sends it is scheduled. */
    suspend fun setDigest(kind: DigestKind, on: Boolean) {
        preferencesRepository.setDigest(kind, on)
        dueReminders.ensureScheduled()
    }

    fun observeAccounts(): Flow<List<AccountWithBalance>> = accountsRepository.observeAll()
}
