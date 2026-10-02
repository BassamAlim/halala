package bassamalim.halala.features.main

import bassamalim.halala.core.ai.AiScheduler
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.data.repositories.RecurringRepository
import bassamalim.halala.core.reminders.DueReminders
import javax.inject.Inject

class MainDomain @Inject constructor(
    private val classificationRepository: ClassificationRepository,
    private val ai: AiScheduler,
    private val recurringRepository: RecurringRepository,
    private val dueReminders: DueReminders
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
        dueReminders.ensureScheduled()
        ai.request()
    }
}
