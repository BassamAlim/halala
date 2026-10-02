package bassamalim.halala.core.data.dataSources.room.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import bassamalim.halala.core.data.dataSources.room.entities.Asset
import bassamalim.halala.core.data.dataSources.room.entities.NetWorthSnapshot
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetsDao {

    @Query("SELECT * FROM assets ORDER BY type, name COLLATE NOCASE")
    fun observeAll(): Flow<List<Asset>>

    @Query("SELECT * FROM assets ORDER BY id")
    suspend fun getAll(): List<Asset>

    @Query("SELECT * FROM assets WHERE id = :id")
    suspend fun get(id: Long): Asset?

    @Insert
    suspend fun insert(asset: Asset): Long

    @Update
    suspend fun update(asset: Asset)

    @Query("DELETE FROM assets WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM net_worth_snapshots ORDER BY date")
    fun observeSnapshots(): Flow<List<NetWorthSnapshot>>

    @Query("SELECT * FROM net_worth_snapshots ORDER BY date")
    suspend fun getSnapshots(): List<NetWorthSnapshot>

    /** One a day: a later one the same day replaces it. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSnapshot(snapshot: NetWorthSnapshot)
}
