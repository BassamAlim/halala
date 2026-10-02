package bassamalim.halala.features.settings

import bassamalim.halala.core.data.dataSources.room.relations.AccountWithBalance
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.PreferencesRepository
import bassamalim.halala.core.models.ReviewSchedule
import bassamalim.halala.core.reminders.ReviewReminders
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SettingsDomain @Inject constructor(
    private val accountsRepository: AccountsRepository,
    private val preferencesRepository: PreferencesRepository,
    private val reviewReminders: ReviewReminders
) {

    fun observeReviewSchedule(): Flow<ReviewSchedule> = preferencesRepository.observeReviewSchedule()

    /** Remembers the schedule and sets the reminder to it. */
    suspend fun setReviewSchedule(schedule: ReviewSchedule) {
        preferencesRepository.setReviewSchedule(schedule)
        reviewReminders.schedule(schedule)
    }


    fun observeAccounts(): Flow<List<AccountWithBalance>> = accountsRepository.observeAll()
}
