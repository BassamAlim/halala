package bassamalim.halala.core.data.dataSources.room.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import bassamalim.halala.core.data.dataSources.room.entities.ZakatProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface ZakatDao {

    @Query("SELECT * FROM zakat_profile WHERE id = 1")
    fun observe(): Flow<ZakatProfile?>

    @Query("SELECT * FROM zakat_profile WHERE id = 1")
    suspend fun get(): ZakatProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(profile: ZakatProfile)
}
