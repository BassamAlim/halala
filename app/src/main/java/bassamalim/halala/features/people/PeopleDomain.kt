package bassamalim.halala.features.people

import bassamalim.halala.core.data.dataSources.room.relations.PersonWithStats
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.data.dataSources.room.entities.Person
import bassamalim.halala.core.data.repositories.LoansRepository
import bassamalim.halala.core.data.repositories.PreferencesRepository
import bassamalim.halala.core.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import bassamalim.halala.core.data.repositories.PeopleRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.domain.LoanState
import bassamalim.halala.core.domain.People
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class PeopleDomain @Inject constructor(
    private val peopleRepository: PeopleRepository,
    private val transactionsRepository: TransactionsRepository,
    private val loansRepository: LoansRepository,
    private val preferencesRepository: PreferencesRepository,
    private val clock: Clock,
    @param:DefaultDispatcher private val default: CoroutineDispatcher
) {

    /** Pairs that may be one person, each with the one that would stay first. */
    fun observeSuggestions(): Flow<List<MergeOffer>> = combine(
        peopleRepository.observePeople(),
        peopleRepository.observeAllAliases(),
        peopleRepository.observeAccountRefs(),
        preferencesRepository.observePeopleSame(),
        preferencesRepository.observePeopleDismissed()
    ) { people, aliases, refs, same, dismissed ->
        val keys = aliases.groupBy({ it.personId }) { it.aliasKey }
        val byId = people.associateBy { it.person.id }
        People.suggest(
            people = people.map { People.Known(it.person.id, it.person.uid, keys[it.person.id].orEmpty()) },
            refs = refs,
            aiSame = same,
            dismissed = dismissed
        ).map { suggestion ->
            val (keep, goes) = listOf(byId.getValue(suggestion.first), byId.getValue(suggestion.second)).sortedWith(STAYS)
            MergeOffer(keep.person, goes.person, suggestion)
        }
    }.flowOn(default)

    suspend fun merge(fromId: Long, intoId: Long) = peopleRepository.merge(fromId, intoId)

    suspend fun dismiss(pairKey: String) = preferencesRepository.dismissPeoplePair(pairKey)

    fun observeLoans(): Flow<List<LoanState>> = loansRepository.observeStates()

    fun observePeople(): Flow<List<PersonWithStats>> = peopleRepository.observePeople()

    fun observeTransactions(): Flow<List<TransactionDetail>> = transactionsRepository.observeAll()

    fun zone(): ZoneId = clock.zone

    fun today(): LocalDate = LocalDate.now(clock)

    /** [goes] would become [keep], for the reason in [suggestion]. */
    data class MergeOffer(val keep: Person, val goes: Person, val suggestion: People.Suggestion)

    companion object {

        /** Who stays when two are merged: the one you named, then the one with more transfers, then the older. */
        val STAYS: Comparator<PersonWithStats> =
            compareByDescending<PersonWithStats> { it.person.namedByYou }.thenByDescending { it.transactions }.thenBy { it.person.id }

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
