package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.room.daos.PeopleDao
import bassamalim.halala.core.data.dataSources.room.entities.Person
import bassamalim.halala.core.data.dataSources.room.entities.PersonAlias
import bassamalim.halala.core.data.dataSources.room.relations.PersonAliasWithCount
import bassamalim.halala.core.data.dataSources.room.relations.PersonWithStats
import bassamalim.halala.core.domain.People
import kotlinx.coroutines.flow.Flow
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

    suspend fun getPeople(): List<Person> = peopleDao.getPeople()

    suspend fun getAliases(): List<PersonAlias> = peopleDao.getAliases()

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
        peopleDao.getPerson(fromId) ?: return
        peopleDao.getPerson(intoId) ?: return
        peopleDao.merge(fromId, intoId)
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
