package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.room.daos.PeopleDao
import bassamalim.halala.core.data.dataSources.room.entities.Person
import bassamalim.halala.core.data.dataSources.room.entities.PersonAlias
import bassamalim.halala.core.data.dataSources.room.relations.PersonAliasWithCount
import bassamalim.halala.core.data.dataSources.room.relations.PersonWithStats
import bassamalim.halala.core.domain.People
import bassamalim.halala.core.sms.ParsedSms
import bassamalim.halala.core.sms.SmsParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The people you send money to and get it from. New names find their person in
 * `ClassificationRepository.applyRules`, with the merchants; here you rename, merge and split
 * them. None of it touches a transaction: transfers find their person through its aliases.
 */
@Singleton
class PeopleRepository @Inject constructor(
    private val peopleDao: PeopleDao
) {

    fun observePeople(): Flow<List<PersonWithStats>> = peopleDao.observePeople()

    fun observePerson(id: Long): Flow<Person?> = peopleDao.observePerson(id)

    fun observeAliases(personId: Long): Flow<List<PersonAliasWithCount>> = peopleDao.observeAliases(personId)

    fun observeAllAliases(): Flow<List<PersonAlias>> = peopleDao.observeAllAliases()

    /**
     * The last four digits banks quoted for each person's account (by person id), read again
     * from their transfers' SMS: they aren't kept anywhere else.
     */
    fun observeAccountRefs(): Flow<Map<Long, Set<String>>> = peopleDao.observeTransferMessages().map { messages ->
        messages.groupBy({ it.personId }) { message ->
            val parsed = SmsParser.bankFor(message.sender)?.let { SmsParser.parse(it, message.body) }
            (parsed as? ParsedSms.Movement)?.partyRefs.orEmpty().filter { it.length == 4 }
        }.mapValues { (_, refs) -> refs.flatten().toSet() }
    }

    suspend fun getPeople(): List<Person> = peopleDao.getPeople()

    suspend fun getAliases(): List<PersonAlias> = peopleDao.getAliases()

    /**
     * Someone no transfer has named yet (to split a bill with): known by the name you give, with
     * no spelling of their own until you merge them with one a bank writes. Returns their id.
     */
    suspend fun add(name: String): Long? {
        val trimmed = name.trim().takeIf { it.isNotEmpty() } ?: return null
        return peopleDao.insertPerson(Person(uid = UUID.randomUUID().toString(), name = trimmed, namedByYou = true))
    }

    /** What you call them. The bank's spellings stay as they were written. */
    suspend fun rename(id: Long, name: String) {
        val person = peopleDao.getPerson(id) ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == person.name) return
        peopleDao.updatePerson(person.copy(name = trimmed, namedByYou = true))
    }

    /**
     * [fromId] is [intoId] by another name: their spellings, and so their transfers, and their
     * loans become [intoId]'s.
     */
    suspend fun merge(fromId: Long, intoId: Long) {
        if (fromId == intoId) return
        val from = peopleDao.getPerson(fromId) ?: return
        val into = peopleDao.getPerson(intoId) ?: return
        // Paying your salary goes with them, from the earlier of the two.
        val since = listOfNotNull(from.salarySince, into.salarySince).minOrNull()
        if (since != into.salarySince) peopleDao.updatePerson(into.copy(salarySince = since))
        peopleDao.merge(fromId, intoId)
    }

    /**
     * [id] pays your salary from [since] on (the transfer you marked): it and their transfers in
     * after it are salary. Null stops it for what comes next; what was salary stays salary.
     */
    suspend fun setSalarySince(id: Long, since: Instant?) {
        val person = peopleDao.getPerson(id) ?: return
        if (person.salarySince != since) peopleDao.updatePerson(person.copy(salarySince = since))
        peopleDao.markSalaries()
    }

    /** "Not this person": one spelling becomes someone of their own. Returns the new person's id. */
    suspend fun split(aliasId: Long): Long? {
        val alias = peopleDao.getAlias(aliasId) ?: return null
        if (peopleDao.getAliases().count { it.personId == alias.personId } < 2) return null
        val personId = peopleDao.insertPerson(
            Person(uid = UUID.randomUUID().toString(), name = People.nameOf(alias.descriptor))
        )
        peopleDao.moveAlias(alias.id, personId)
        return personId
    }
}
