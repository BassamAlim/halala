package bassamalim.halala.features.main

import bassamalim.halala.core.ai.AiScheduler
import bassamalim.halala.core.data.repositories.ClassificationRepository
import javax.inject.Inject

class MainDomain @Inject constructor(
    private val classificationRepository: ClassificationRepository,
    private val ai: AiScheduler
) {

    /**
     * Catches up as the app opens: new descriptors find their merchant, well-known merchants are
     * identified and the rules file what they can, then the AI is asked about the rest (when it
     * is on). After an upgrade, this is what fills in the merchants of the history.
     */
    suspend fun catchUp() {
        classificationRepository.applyRules()
        ai.request()
    }
}
