package bassamalim.halala.core.data.dataSources.room.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import bassamalim.halala.core.data.dataSources.room.entities.Budget
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetsDao {

    @Query("SELECT * FROM budgets ORDER BY id")
    fun observeAll(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets ORDER BY id")
    suspend fun getAll(): List<Budget>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun get(id: Long): Budget?

    @Insert
    suspend fun insert(budget: Budget): Long

    @Update
    suspend fun update(budget: Budget)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun delete(id: Long)
}
