package bassamalim.halala.core.data.dataSources.room.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import bassamalim.halala.core.data.dataSources.room.entities.TransactionPlace
import kotlinx.coroutines.flow.Flow

@Dao
interface PlacesDao {

    @Query("SELECT * FROM transaction_places")
    fun observeAll(): Flow<List<TransactionPlace>>

    @Query("SELECT * FROM transaction_places ORDER BY transactionId")
    suspend fun getAll(): List<TransactionPlace>

    @Query("SELECT * FROM transaction_places WHERE transactionId = :transactionId")
    fun observe(transactionId: Long): Flow<TransactionPlace?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(rows: List<TransactionPlace>)

    @Query("DELETE FROM transaction_places WHERE transactionId = :transactionId")
    suspend fun delete(transactionId: Long)

    @Query("DELETE FROM transaction_places")
    suspend fun deleteAll()
}
