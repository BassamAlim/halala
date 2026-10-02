package bassamalim.halala.core.data.dataSources.room.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import bassamalim.halala.core.data.dataSources.room.entities.BalanceCheckpoint
import bassamalim.halala.core.data.dataSources.room.entities.DismissedAlert
import bassamalim.halala.core.data.dataSources.room.entities.RawMessage
import bassamalim.halala.core.enums.RawStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertsDao {

    @Query("SELECT `key` FROM dismissed_alerts")
    fun observeDismissed(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun dismiss(alert: DismissedAlert)

    @Query("SELECT * FROM balance_checkpoints ORDER BY at")
    fun observeCheckpoints(): Flow<List<BalanceCheckpoint>>

    @Query("SELECT * FROM raw_messages WHERE status = :status ORDER BY receivedAt DESC LIMIT 50")
    fun observeByStatus(status: RawStatus): Flow<List<RawMessage>>
}
