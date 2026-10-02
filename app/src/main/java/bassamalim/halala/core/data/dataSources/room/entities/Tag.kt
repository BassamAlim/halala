package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * A tag cuts across categories ("Trip to Türkiye", "Wedding"). One with [auto] set is applied
 * to everything from [startsOn] to [endsOn] (to today while it has no end) as it arrives.
 */
@Entity(tableName = "tags", indices = [Index(value = ["uid"], unique = true)])
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val name: String,
    val startsOn: LocalDate? = null,
    val endsOn: LocalDate? = null,
    val auto: Boolean = false,
    val createdAt: Instant
)

/**
 * A transaction carries a tag. [removed] is one you took off a transaction an automatic tag
 * covers, so the tag doesn't put it back.
 */
@Entity(
    tableName = "transaction_tags",
    primaryKeys = ["transactionId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = Transaction::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Tag::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["tagId"])]
)
data class TransactionTag(
    val transactionId: Long,
    val tagId: Long,
    @ColumnInfo(defaultValue = "0")
    val removed: Boolean = false
)
