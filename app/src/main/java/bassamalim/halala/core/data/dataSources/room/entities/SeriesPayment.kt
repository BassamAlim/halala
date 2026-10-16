package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A transaction you said pays a subscription, bill or planned payment: rent sent by a transfer
 * that names no one, say. It counts as one of the series' charges whatever it was paid to, and
 * not toward any other series. A transaction pays one series at most.
 */
@Entity(
    tableName = "series_payments",
    foreignKeys = [
        ForeignKey(entity = Transaction::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = RecurringSeries::class, parentColumns = ["id"], childColumns = ["seriesId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["seriesId"])]
)
data class SeriesPayment(
    @PrimaryKey val transactionId: Long,
    val seriesId: Long
)
