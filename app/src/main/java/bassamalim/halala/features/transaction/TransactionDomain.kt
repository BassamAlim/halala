package bassamalim.halala.features.transaction

import bassamalim.halala.core.data.dataSources.room.entities.Category
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.data.repositories.GoalsRepository
import bassamalim.halala.core.data.dataSources.room.entities.GoalContribution
import bassamalim.halala.core.data.dataSources.room.entities.SavingsGoal
import bassamalim.halala.core.enums.AccountType
import kotlinx.coroutines.flow.map
import bassamalim.halala.core.data.repositories.LoansRepository
import bassamalim.halala.core.data.repositories.PeopleRepository
import bassamalim.halala.core.data.repositories.PlacesRepository
import bassamalim.halala.core.data.dataSources.room.entities.TransactionPlace
import bassamalim.halala.core.places.LocationAccess
import bassamalim.halala.core.places.PlaceCapture
import bassamalim.halala.core.data.dataSources.room.relations.PersonWithStats
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.domain.DescribedRule
import bassamalim.halala.core.domain.LoanState
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
    private val loansRepository: LoansRepository,
    private val peopleRepository: PeopleRepository,
    private val placesRepository: PlacesRepository,
    private val placeCapture: PlaceCapture,
    private val goalsRepository: GoalsRepository,
    private val clock: Clock
) {

    /** Where it was made, when that was kept. */
    fun observePlace(id: Long): Flow<TransactionPlace?> = placesRepository.observe(id)

    /** Whether Halala may keep where purchases happen, which is why one has no place. */
    fun locationAccess(): LocationAccess = placeCapture.access()

    fun observeGoals(): Flow<List<SavingsGoal>> = goalsRepository.observeAll()

    fun observeContributions(): Flow<List<GoalContribution>> = goalsRepository.observeContributions()

    fun observeAccountTypes(): Flow<Map<Long, AccountType>> =
        accountsRepository.observeAll().map { accounts -> accounts.associate { it.account.id to it.account.type } }

    /** It went toward [goalId] (or came out of it): saving, not spending or income. */
    suspend fun contribute(id: Long, goalId: Long, withdrawn: Boolean) = goalsRepository.contribute(id, goalId, withdrawn)

    /** Not toward a goal: it counts as it did before. */
    suspend fun uncontribute(id: Long) = goalsRepository.uncontribute(id)

    fun observePeople(): Flow<List<PersonWithStats>> = peopleRepository.observePeople()

    /** Its shares (person → minor units) become loans owed to you. */
    suspend fun split(id: Long, shares: Map<Long, Long>) = loansRepository.split(id, shares)

    suspend fun unsplit(id: Long) = loansRepository.unsplit(id)

    /** [personId] pays your salary: transaction [id] and their transfers in after it are salary. */
    suspend fun markSalary(id: Long, personId: Long) {
        val tx = transactionsRepository.get(id) ?: return
        peopleRepository.setSalarySince(personId, tx.occurredAt)
    }

    /** Someone to split with whom no transfer has named: their id. */
    suspend fun addPerson(name: String) = peopleRepository.add(name)

    fun observeLoans(): Flow<List<LoanState>> = loansRepository.observeStates()

    /** It lent (or borrowed) money: a new loan with [personId], or the person it names. */
    suspend fun openLoan(id: Long, dueOn: LocalDate?, personId: Long?) = loansRepository.open(id, dueOn, personId)

    /** You paid all of it for [personId]: one share of the whole, owed to you. */
    suspend fun paidFor(id: Long, personId: Long, totalMinor: Long, dueOn: LocalDate?) =
        loansRepository.split(id, mapOf(personId to totalMinor), dueOn)

    /** It pays [loanId] back. */
    suspend fun repay(loanId: Long, id: Long) = loansRepository.repay(loanId, id)

    /** It is a plain transfer again (and, if it was all that was lent, the loan goes). */
    suspend fun unlinkLoan(id: Long) = loansRepository.unlink(id)


    fun observe(id: Long): Flow<TransactionDetail?> = transactionsRepository.observe(id)

    /**
     * A move goes as a whole: both legs. A transfer leaves its loan first, so a loan never
     * outlives the money it lent (its repayments become plain transfers again); a split
     * purchase is unsplit first, for the same reason.
     */
    suspend fun delete(id: Long) {
        loansRepository.unlink(id)
        loansRepository.unsplit(id)
        transactionsRepository.delete(id)
    }

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
