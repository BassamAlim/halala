package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import bassamalim.halala.core.enums.AccountType
import java.time.Instant

/**
 * An account you named. Several can sit at one bank; the last four digits the SMS quotes tell
 * them apart, hence the unique index (SQLite lets any number of null last-4s through, which is
 * what the cash wallet has).
 *
 * Accounts are archived, never deleted: transactions point at them.
 */
@Entity(
    tableName = "accounts",
    foreignKeys = [
        ForeignKey(
            entity = Institution::class,
            parentColumns = ["id"],
            childColumns = ["institutionId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["uid"], unique = true),
        Index(value = ["institutionId", "last4"], unique = true)
    ]
)
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable across exports and phones, so a re-import merges rather than duplicates. */
    val uid: String,
    /** Null for the cash wallet, which no bank holds. */
    val institutionId: Long?,
    val nickname: String,
    val type: AccountType,
    val last4: String? = null,
    val ibanSuffix: String? = null,
    /** ISO 4217. Every amount on this account is in it. */
    val currency: String,
    /** In minor units (halalas for SAR), like every amount in the app. */
    val openingBalanceMinor: Long = 0,
    val archived: Boolean = false,
    val createdAt: Instant
)
