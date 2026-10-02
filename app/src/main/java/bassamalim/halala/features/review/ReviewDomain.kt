package bassamalim.halala.features.review

import bassamalim.halala.core.data.dataSources.room.entities.Category
import bassamalim.halala.core.data.dataSources.room.entities.Merchant
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.domain.Identification
import bassamalim.halala.core.domain.Tier
import bassamalim.halala.core.enums.BusinessType
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

    /** What each merchant was identified as: the suggestion and its evidence. */
    fun observeMerchants(): Flow<List<Merchant>> = classificationRepository.observeAllMerchants()

    /** One answer for the merchant: a rule, applied to its past and to what comes. Returns the batch to undo. */
    suspend fun learn(merchant: String, categoryId: Long): Long? = classificationRepository.learn(merchant, categoryId)

    suspend fun undo(batchId: Long) = classificationRepository.undo(batchId)

    fun zone(): ZoneId = clock.zone

    fun today(): LocalDate = LocalDate.now(clock)

    companion object {

        /**
         * The category chosen for you on [merchant]'s card: the one that takes what it was
         * identified as, when that is sure enough to suggest. None for a merchant that needs you.
         */
        fun suggestionFor(merchant: Merchant?, categories: List<Category>): Category? {
            if (merchant == null || Identification.tierOf(merchant, categories) == Tier.ASK) return null
            return Identification.categoryFor(merchant.businessType, categories)
        }

        /** What is shown as evidence: a business type the merchant is known to be. */
        fun evidenceOf(merchant: Merchant?): BusinessType? =
            merchant?.businessType?.takeIf { it != BusinessType.UNKNOWN }
    }
}
