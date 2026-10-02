package bassamalim.halala.features.review

import bassamalim.halala.core.data.dataSources.room.entities.Category
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class ReviewDomain @Inject constructor(
    private val transactionsRepository: TransactionsRepository,
    private val classificationRepository: ClassificationRepository,
    private val clock: Clock
) {

    fun observeTransactions(): Flow<List<TransactionDetail>> = transactionsRepository.observeAll()

    fun observeCategories(): Flow<List<Category>> = classificationRepository.observeCategories()

    /** One answer for the merchant: a rule, applied to its past and to what comes. */
    suspend fun learn(merchant: String, categoryId: Long) = classificationRepository.learn(merchant, categoryId)

    fun zone(): ZoneId = clock.zone

    fun today(): LocalDate = LocalDate.now(clock)
}
