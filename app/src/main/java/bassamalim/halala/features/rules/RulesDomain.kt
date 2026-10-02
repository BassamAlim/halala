package bassamalim.halala.features.rules

import bassamalim.halala.core.data.dataSources.room.entities.Category
import bassamalim.halala.core.data.dataSources.room.relations.RuleWithStats
import bassamalim.halala.core.data.repositories.ClassificationRepository
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class RulesDomain @Inject constructor(
    private val classificationRepository: ClassificationRepository,
    private val clock: Clock
) {

    fun observeRules(): Flow<List<RuleWithStats>> = classificationRepository.observeRules()

    fun observeCategories(): Flow<List<Category>> = classificationRepository.observeCategories()

    suspend fun retarget(ruleId: Long, categoryId: Long) = classificationRepository.retarget(ruleId, categoryId)

    suspend fun setEnabled(ruleId: Long, enabled: Boolean) = classificationRepository.setEnabled(ruleId, enabled)

    suspend fun delete(ruleId: Long) = classificationRepository.delete(ruleId)

    fun zone(): ZoneId = clock.zone

    fun today(): LocalDate = LocalDate.now(clock)
}
