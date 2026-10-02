package bassamalim.halala.features.main

import bassamalim.halala.core.ai.AiScheduler
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.Globals
import bassamalim.halala.core.data.repositories.AssetsRepository
import bassamalim.halala.core.data.repositories.TagsRepository
import bassamalim.halala.core.data.repositories.RecurringRepository
import bassamalim.halala.core.reminders.DueReminders
import javax.inject.Inject

class MainDomain @Inject constructor(
    private val classificationRepository: ClassificationRepository,
    private val ai: AiScheduler,
    private val recurringRepository: RecurringRepository,
    private val dueReminders: DueReminders,
    private val assetsRepository: AssetsRepository,
    private val tagsRepository: TagsRepository
) {

    /**
     * Catches up as the app opens: new descriptors find their merchant, well-known merchants are
     * identified and the rules file what they can, then the AI is asked about the rest (when it
     * is on). After an upgrade, this is what fills in the merchants of the history. Then what
     * repeats is proposed as a subscription, bill or planned payment.
     */
    suspend fun catchUp() {
        classificationRepository.applyRules()
        recurringRepository.detect()
        tagsRepository.applyActive()
        dueReminders.ensureScheduled()
        assetsRepository.snapshot(Globals.PRIMARY_CURRENCY)
        ai.request()
    }
}
