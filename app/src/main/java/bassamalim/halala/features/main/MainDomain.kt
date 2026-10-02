package bassamalim.halala.features.main

import bassamalim.halala.core.data.repositories.ClassificationRepository
import javax.inject.Inject

class MainDomain @Inject constructor(
    private val classificationRepository: ClassificationRepository
) {

    /**
     * Catches up as the app opens: new descriptors find their merchant and the rules file what
     * they can. After an upgrade, this is what fills in the merchants of the history.
     */
    suspend fun catchUp() = classificationRepository.applyRules()
}
