package bassamalim.halala.features.categorySpending

import bassamalim.halala.core.data.dataSources.room.entities.Category
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.features.insights.InsightsDomain
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** What one category's spending in one month is made of, as Insights counts it. */
class CategorySpendingDomain @Inject constructor(
    private val transactionsRepository: TransactionsRepository,
    private val classificationRepository: ClassificationRepository,
    private val clock: Clock
) {

    fun observeTransactions(): Flow<List<TransactionDetail>> = transactionsRepository.observeAll()

    fun observeCategories(): Flow<List<Category>> = classificationRepository.observeCategories()

    fun zone(): ZoneId = clock.zone

    fun today(): LocalDate = LocalDate.now(clock)

    companion object {

        /** [month]'s spending in [currency] filed under [categoryId] (null: not yet filed), newest first. */
        fun of(
            details: List<TransactionDetail>,
            categoryId: Long?,
            month: YearMonth,
            currency: String,
            zone: ZoneId
        ): List<TransactionDetail> = InsightsDomain.spending(details, currency).filter {
            it.transaction.categoryId == categoryId &&
                    YearMonth.from(it.transaction.occurredAt.atZone(zone)) == month
        }
    }
}
