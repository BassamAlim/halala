package bassamalim.halala.features.people

import bassamalim.halala.core.data.dataSources.room.relations.PersonWithStats
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.data.repositories.PeopleRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.domain.People
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class PeopleDomain @Inject constructor(
    private val peopleRepository: PeopleRepository,
    private val transactionsRepository: TransactionsRepository,
    private val clock: Clock
) {

    fun observePeople(): Flow<List<PersonWithStats>> = peopleRepository.observePeople()

    fun observeTransactions(): Flow<List<TransactionDetail>> = transactionsRepository.observeAll()

    fun zone(): ZoneId = clock.zone

    fun today(): LocalDate = LocalDate.now(clock)

    companion object {

        /** What went to and came from each person (by id), in [currency]. */
        // ponytail: one currency (SAR); a transfer abroad shows in the person's feed, not the sums.
        fun flowsByPerson(details: List<TransactionDetail>, currency: String): Map<Long, People.Flow> =
            details
                .filter { it.personId != null && it.transaction.currency == currency }
                .groupBy({ it.personId!! }, { it.transaction.direction to it.transaction.amountMinor })
                .mapValues { (_, transfers) -> People.flowOf(transfers) }

        /** The people whose name holds [query]: everyone when it is blank. */
        fun matching(people: List<PersonWithStats>, query: String): List<PersonWithStats> {
            val needle = query.trim()
            return people.filter { it.person.name.contains(needle, ignoreCase = true) }
        }
    }
}
