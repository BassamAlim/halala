package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Digits a bank's SMS quote for one of your accounts beyond its own last four: a card on it, or
 * the bank's other way of writing its number. Learned when you answer "Which account is ••1234?".
 */
@Entity(
    tableName = "account_refs",
    foreignKeys = [
        ForeignKey(
            entity = Institution::class,
            parentColumns = ["id"],
            childColumns = ["institutionId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["institutionId", "ref"], unique = true), Index(value = ["accountId"])]
)
data class AccountRef(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val institutionId: Long,
    val ref: String,
    val accountId: Long
)
