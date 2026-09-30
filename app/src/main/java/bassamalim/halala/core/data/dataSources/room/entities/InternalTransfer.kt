package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The two legs of one move between your own accounts. Paired legs are left out of spending and
 * income. A leg belongs to at most one pair.
 */
@Entity(
    tableName = "internal_transfers",
    foreignKeys = [
        ForeignKey(
            entity = Transaction::class,
            parentColumns = ["id"],
            childColumns = ["outTransactionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Transaction::class,
            parentColumns = ["id"],
            childColumns = ["inTransactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uid"], unique = true),
        Index(value = ["outTransactionId"], unique = true),
        Index(value = ["inTransactionId"], unique = true)
    ]
)
data class InternalTransfer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val outTransactionId: Long,
    val inTransactionId: Long,
    /** 1.0 when you entered the move yourself; SMS pairing (Phase 1) is less sure. */
    val matchConfidence: Double = 1.0
)
