package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import java.time.Instant

/**
 * The core record: money that moved on one account. [amountMinor] is always positive and
 * [direction] carries the sign, so a sum can never be confused by a stored minus.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = RawMessage::class,
            parentColumns = ["id"],
            childColumns = ["rawMessageId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["uid"], unique = true),
        Index(value = ["accountId"]),
        Index(value = ["occurredAt"]),
        Index(value = ["rawMessageId"])
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val accountId: Long,
    val direction: Direction,
    /** Integer minor units, never a float. Always > 0. */
    val amountMinor: Long,
    /** Always the account's currency (the repository sets it). */
    val currency: String,
    val occurredAt: Instant,
    val kind: TransactionKind,
    /** Merchant or payee as written: "Panda", "Mother". Blank when unsaid. */
    val title: String = "",
    val note: String = "",
    val source: TransactionSource,
    val createdAt: Instant,
    /** The SMS it was parsed from (both legs of a one-SMS move point at it). */
    val rawMessageId: Long? = null,
    /** A foreign charge: what the merchant asked for, before the bank converted it. */
    val originalAmountMinor: Long? = null,
    val originalCurrency: String? = null
)
