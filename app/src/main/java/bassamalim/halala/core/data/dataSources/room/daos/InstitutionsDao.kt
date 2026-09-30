package bassamalim.halala.core.data.dataSources.room.daos

import androidx.room.Dao
import androidx.room.Query
import bassamalim.halala.core.data.dataSources.room.entities.Institution
import kotlinx.coroutines.flow.Flow

@Dao
interface InstitutionsDao {

    @Query("SELECT * FROM institutions ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Institution>>

    @Query("SELECT * FROM institutions ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<Institution>

    @Query("SELECT * FROM institutions WHERE id = :id")
    suspend fun get(id: Long): Institution?
}
