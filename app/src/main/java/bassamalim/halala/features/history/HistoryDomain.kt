package bassamalim.halala.features.history

import bassamalim.halala.core.data.dataSources.room.relations.BatchWithCount
import bassamalim.halala.core.data.repositories.ClassificationRepository
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class HistoryDomain @Inject constructor(
    private val classificationRepository: ClassificationRepository,
    private val clock: Clock
) {

    fun observeBatches(): Flow<List<BatchWithCount>> = classificationRepository.observeBatches()

    suspend fun undo(batchId: Long) = classificationRepository.undo(batchId)

    fun zone(): ZoneId = clock.zone

    fun today(): LocalDate = LocalDate.now(clock)
}
