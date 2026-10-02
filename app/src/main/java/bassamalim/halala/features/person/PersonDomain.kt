package bassamalim.halala.features.person

import bassamalim.halala.core.data.dataSources.room.entities.Person
import bassamalim.halala.core.data.dataSources.room.relations.PersonAliasWithCount
import bassamalim.halala.core.data.dataSources.room.relations.PersonWithStats
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.data.repositories.PeopleRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class NameProblem { Missing }

class PersonDomain @Inject constructor(
    private val peopleRepository: PeopleRepository,
    private val transactionsRepository: TransactionsRepository,
    private val clock: Clock
) {

    fun observePerson(id: Long): Flow<Person?> = peopleRepository.observePerson(id)

    fun observeAliases(id: Long): Flow<List<PersonAliasWithCount>> = peopleRepository.observeAliases(id)

    fun observePeople(): Flow<List<PersonWithStats>> = peopleRepository.observePeople()

    fun observeTransactions(): Flow<List<TransactionDetail>> = transactionsRepository.observeAll()

    /** Checks and writes; the problem when there is one. */
    suspend fun rename(id: Long, name: String): NameProblem? {
        validateName(name)?.let { return it }
        peopleRepository.rename(id, name)
        return null
    }

    suspend fun merge(fromId: Long, intoId: Long) = peopleRepository.merge(fromId, intoId)

    suspend fun split(aliasId: Long) = peopleRepository.split(aliasId)

    fun zone(): ZoneId = clock.zone

    fun today(): LocalDate = LocalDate.now(clock)

    companion object {

        fun validateName(name: String): NameProblem? = if (name.isBlank()) NameProblem.Missing else null

        /**
         * The people this one could be merged into: everyone else whose name holds [query], the
         * latest first, the first [limit] of them (typing narrows the rest).
         */
        fun mergeOptions(
            people: List<PersonWithStats>,
            exceptId: Long,
            query: String,
            limit: Int = 8
        ): List<PersonWithStats> {
            val needle = query.trim()
            return people
                .filter { it.person.id != exceptId && it.person.name.contains(needle, ignoreCase = true) }
                .take(limit)
        }
    }
}
