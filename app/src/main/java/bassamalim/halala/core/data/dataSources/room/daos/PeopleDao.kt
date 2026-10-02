package bassamalim.halala.core.data.dataSources.room.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import bassamalim.halala.core.data.dataSources.room.entities.Person
import bassamalim.halala.core.data.dataSources.room.entities.PersonAlias
import bassamalim.halala.core.data.dataSources.room.relations.PersonAliasWithCount
import bassamalim.halala.core.data.dataSources.room.relations.PersonWithStats
import kotlinx.coroutines.flow.Flow

/** The kinds whose title names a person, as SQL: `People.KINDS`. */
const val PERSON_KINDS = "'TRANSFER_OUT', 'TRANSFER_IN', 'LOAN_GIVEN', 'LOAN_RECEIVED', 'LOAN_REPAYMENT'"

/**
 * The transfers (`t`) whose title names a person: money to or from someone, not a move between
 * your own accounts.
 */
const val PERSON_TRANSFER = "t.kind IN ($PERSON_KINDS) " +
        "AND NOT EXISTS (SELECT 1 FROM internal_transfers x WHERE x.outTransactionId = t.id OR x.inTransactionId = t.id)"

/** People and the names (aliases) each is known by. */
@Dao
interface PeopleDao {

    @Query(
        """
        SELECT p.*,
            (SELECT COUNT(*) FROM transactions t JOIN person_aliases a ON a.aliasKey = t.merchantKey
                WHERE a.personId = p.id AND $PERSON_TRANSFER) AS transactions,
            (SELECT MAX(t.occurredAt) FROM transactions t JOIN person_aliases a ON a.aliasKey = t.merchantKey
                WHERE a.personId = p.id AND $PERSON_TRANSFER) AS lastAt
        FROM people p ORDER BY lastAt DESC, p.name COLLATE NOCASE
        """
    )
    fun observePeople(): Flow<List<PersonWithStats>>

    @Query("SELECT * FROM people WHERE id = :id")
    fun observePerson(id: Long): Flow<Person?>

    @Query(
        """
        SELECT a.*, (SELECT COUNT(*) FROM transactions t WHERE t.merchantKey = a.aliasKey AND $PERSON_TRANSFER) AS transactions
        FROM person_aliases a WHERE a.personId = :personId ORDER BY transactions DESC, a.id
        """
    )
    fun observeAliases(personId: Long): Flow<List<PersonAliasWithCount>>

    @Query("SELECT * FROM people ORDER BY id")
    suspend fun getPeople(): List<Person>

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun getPerson(id: Long): Person?

    @Insert
    suspend fun insertPerson(person: Person): Long

    @Update
    suspend fun updatePerson(person: Person)

    /** Their aliases go with them (they cascade). */
    @Query("DELETE FROM people WHERE id = :id")
    suspend fun deletePerson(id: Long)

    @Query("SELECT * FROM person_aliases ORDER BY id")
    suspend fun getAliases(): List<PersonAlias>

    @Query("SELECT * FROM person_aliases WHERE id = :id")
    suspend fun getAlias(id: Long): PersonAlias?

    /** A key already taken keeps its person. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlias(alias: PersonAlias): Long

    @Query("UPDATE person_aliases SET personId = :personId WHERE id = :id")
    suspend fun moveAlias(id: Long, personId: Long)

    @Query("UPDATE person_aliases SET personId = :intoId WHERE personId = :fromId")
    suspend fun moveAliases(fromId: Long, intoId: Long)

    @Query("UPDATE loans SET personId = :intoId WHERE personId = :fromId")
    suspend fun moveLoans(fromId: Long, intoId: Long)

    /** One person becomes another: their spellings and loans move, then they go, as one write. */
    @androidx.room.Transaction
    suspend fun merge(fromId: Long, intoId: Long) {
        moveAliases(fromId, intoId)
        moveLoans(fromId, intoId)
        deletePerson(fromId)
    }

    /** Each person transfer's title and key, oldest first: the first spelling names the person. */
    @Query("SELECT t.title, t.merchantKey FROM transactions t WHERE t.title != '' AND $PERSON_TRANSFER ORDER BY t.occurredAt, t.id")
    suspend fun getTransferNames(): List<TransferName>
}

/** A transfer's title as the bank wrote it, and its key. */
data class TransferName(val title: String, val merchantKey: String)
