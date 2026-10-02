package bassamalim.halala.core.data.dataSources.room.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import bassamalim.halala.core.data.dataSources.room.entities.SavingsTerms
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsDao {

    @Query("SELECT * FROM savings_terms")
    fun observeAll(): Flow<List<SavingsTerms>>

    @Query("SELECT * FROM savings_terms")
    suspend fun getAll(): List<SavingsTerms>

    @Query("SELECT * FROM savings_terms WHERE accountId = :accountId")
    suspend fun get(accountId: Long): SavingsTerms?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(terms: SavingsTerms)

    @Query("DELETE FROM savings_terms WHERE accountId = :accountId")
    suspend fun delete(accountId: Long)
}
