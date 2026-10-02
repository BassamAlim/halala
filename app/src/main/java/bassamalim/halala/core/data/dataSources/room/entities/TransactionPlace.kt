package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Where the phone was when a purchase's SMS arrived (only while "Remember where you spend" is
 * on): degrees × 10⁷ as integers, and how sure the phone was, in metres. Never sent anywhere.
 */
@Entity(
    tableName = "transaction_places",
    foreignKeys = [ForeignKey(entity = Transaction::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.CASCADE)]
)
data class TransactionPlace(
    @PrimaryKey val transactionId: Long,
    val latitudeE7: Int,
    val longitudeE7: Int,
    val accuracyMeters: Int
) {
    val latitude get() = latitudeE7 / E7
    val longitude get() = longitudeE7 / E7

    companion object {
        const val E7 = 10_000_000.0

        fun of(transactionId: Long, latitude: Double, longitude: Double, accuracy: Float) = TransactionPlace(
            transactionId, Math.round(latitude * E7).toInt(), Math.round(longitude * E7).toInt(), accuracy.toInt()
        )
    }
}
