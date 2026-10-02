package bassamalim.halala.core.data.dataSources.room.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import bassamalim.halala.core.data.dataSources.room.entities.RetirementScenario
import kotlinx.coroutines.flow.Flow

@Dao
interface ScenariosDao {

    @Query("SELECT * FROM retirement_scenarios ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<RetirementScenario>>

    @Query("SELECT * FROM retirement_scenarios ORDER BY id")
    suspend fun getAll(): List<RetirementScenario>

    @Insert
    suspend fun insert(scenario: RetirementScenario): Long

    @Query("DELETE FROM retirement_scenarios WHERE id = :id")
    suspend fun delete(id: Long)
}
