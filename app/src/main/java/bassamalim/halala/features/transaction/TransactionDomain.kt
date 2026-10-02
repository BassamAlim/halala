package bassamalim.halala.features.transaction

import bassamalim.halala.core.data.dataSources.room.entities.Category
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.domain.DescribedRule
import bassamalim.halala.core.domain.Rules
import bassamalim.halala.core.enums.ExpenseType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class TransactionDomain @Inject constructor(
    private val transactionsRepository: TransactionsRepository,
    private val classificationRepository: ClassificationRepository,
    private val accountsRepository: AccountsRepository,
    private val clock: Clock
) {

    fun observe(id: Long): Flow<TransactionDetail?> = transactionsRepository.observe(id)

    /** A move goes as a whole: both legs. */
    suspend fun delete(id: Long) = transactionsRepository.delete(id)

    fun observeCategories(): Flow<List<Category>> = classificationRepository.observeCategories()

    fun observeRules(): Flow<List<DescribedRule>> = combine(
        classificationRepository.observeRules(),
        accountsRepository.observeAll(),
        Rules::describe
    )

    /** Your answer for this one transaction, with the category's own type. */
    suspend fun file(id: Long, categoryId: Long) =
        classificationRepository.file(id, categoryId, classificationRepository.defaultTypeOf(categoryId))

    suspend fun setType(id: Long, categoryId: Long?, type: ExpenseType) =
        classificationRepository.file(id, categoryId, type, typeOnly = true)

    /** Your answer for this one and, as a rule, for the merchant's others and the ones to come. */
    suspend fun fileAlways(id: Long, merchant: String, categoryId: Long) =
        classificationRepository.learn(merchant, categoryId, alsoFile = id)

    /** How many of the merchant's other transactions "always" would file. */
    suspend fun othersFrom(merchant: String, id: Long): Int = classificationRepository.countFor(merchant, exceptId = id)

    fun zone(): ZoneId = clock.zone

    fun today(): LocalDate = LocalDate.now(clock)
}
