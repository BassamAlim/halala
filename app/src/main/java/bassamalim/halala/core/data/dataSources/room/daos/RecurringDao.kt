package bassamalim.halala.core.data.dataSources.room.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import bassamalim.halala.core.data.dataSources.room.entities.RecurringSeries
import bassamalim.halala.core.data.dataSources.room.entities.SeriesPayment
import kotlinx.coroutines.flow.Flow

/** Subscriptions, bills and planned payments. */
@Dao
interface RecurringDao {

    @Query("SELECT * FROM recurring_series ORDER BY id")
    fun observeAll(): Flow<List<RecurringSeries>>

    @Query("SELECT * FROM recurring_series WHERE id = :id")
    fun observe(id: Long): Flow<RecurringSeries?>

    @Query("SELECT * FROM recurring_series ORDER BY id")
    suspend fun getAll(): List<RecurringSeries>

    @Query("SELECT * FROM recurring_series WHERE id = :id")
    suspend fun get(id: Long): RecurringSeries?

    @Insert
    suspend fun insert(series: RecurringSeries): Long

    @Update
    suspend fun update(series: RecurringSeries)

    @Query("DELETE FROM recurring_series WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM series_payments ORDER BY transactionId")
    fun observePayments(): Flow<List<SeriesPayment>>

    @Query("SELECT * FROM series_payments ORDER BY transactionId")
    suspend fun getPayments(): List<SeriesPayment>

    /** A transaction pays one series at most: linking it again moves it. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: SeriesPayment)

    @Query("DELETE FROM series_payments WHERE transactionId = :transactionId")
    suspend fun deletePayment(transactionId: Long)
}
