package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A balance the bank reported ("Remaining balance: 80.47 SAR"). An account's balance is its
 * latest checkpoint plus what happened after it, so a missed SMS can't drift it for long.
 */
@Entity(
    tableName = "balance_checkpoints",
    foreignKeys = [
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RawMessage::class,
            parentColumns = ["id"],
            childColumns = ["rawMessageId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["accountId", "at"]), Index(value = ["rawMessageId"])]
)
data class BalanceCheckpoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    /** Minor units of the account's currency; can be negative. */
    val balanceMinor: Long,
    /** The moment the balance holds for: right after the transaction that reported it. */
    val at: Instant,
    val rawMessageId: Long?
)
