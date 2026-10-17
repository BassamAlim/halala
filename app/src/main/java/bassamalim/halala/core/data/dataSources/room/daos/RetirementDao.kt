package bassamalim.halala.core.data.dataSources.room.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import bassamalim.halala.core.data.dataSources.room.entities.RetirementPlan
import kotlinx.coroutines.flow.Flow

@Dao
interface RetirementDao {

    @Query("SELECT * FROM retirement_plan WHERE id = 1")
    fun observe(): Flow<RetirementPlan?>

    @Query("SELECT * FROM retirement_plan WHERE id = 1")
    suspend fun get(): RetirementPlan?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(plan: RetirementPlan)
}
