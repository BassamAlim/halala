package bassamalim.halala.core.data.dataSources.room.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import bassamalim.halala.core.data.dataSources.room.entities.Tag
import bassamalim.halala.core.data.dataSources.room.entities.TransactionTag
import kotlinx.coroutines.flow.Flow

@Dao
interface TagsDao {

    @Query("SELECT * FROM tags ORDER BY COALESCE(startsOn, createdAt / 86400000) DESC, id DESC")
    fun observeAll(): Flow<List<Tag>>

    @Query("SELECT * FROM tags ORDER BY id")
    suspend fun getAll(): List<Tag>

    @Query("SELECT * FROM tags WHERE id = :id")
    fun observe(id: Long): Flow<Tag?>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun get(id: Long): Tag?

    @Query("SELECT * FROM transaction_tags")
    fun observeRows(): Flow<List<TransactionTag>>

    @Query("SELECT * FROM transaction_tags ORDER BY transactionId, tagId")
    suspend fun getRows(): List<TransactionTag>

    @Query("SELECT * FROM transaction_tags WHERE transactionId = :transactionId")
    suspend fun rowsFor(transactionId: Long): List<TransactionTag>

    @Insert
    suspend fun insert(tag: Tag): Long

    @Update
    suspend fun update(tag: Tag)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: TransactionTag)

    /** Adds what isn't there; a row you removed stays removed. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(rows: List<TransactionTag>)

    @Query("DELETE FROM transaction_tags WHERE transactionId = :transactionId AND tagId = :tagId")
    suspend fun deleteRow(transactionId: Long, tagId: Long)
}
