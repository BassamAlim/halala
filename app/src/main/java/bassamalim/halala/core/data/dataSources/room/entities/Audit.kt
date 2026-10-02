package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import bassamalim.halala.core.enums.AuditAction
import bassamalim.halala.core.enums.AuditEntity
import java.time.Instant

/**
 * One thing you did to how transactions are filed, as a whole: choosing "always" is one batch,
 * however many transactions it filed. [subject] is the merchant, rule or category it was about
 * and [detail] the category it filed under, as they read at the time.
 */
@Entity(tableName = "audit_batches")
data class AuditBatch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val action: AuditAction,
    val subject: String,
    val detail: String = "",
    val at: Instant,
    val undoneAt: Instant? = null
)

/**
 * One row a batch changed, before and after, as JSON: null [old] means the batch created it,
 * null [new] that it deleted it. Undo puts [old] back wherever the row still reads [new].
 */
@Entity(
    tableName = "audit_changes",
    foreignKeys = [
        ForeignKey(
            entity = AuditBatch::class,
            parentColumns = ["id"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["batchId"])]
)
data class AuditChange(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: Long,
    val entity: AuditEntity,
    val entityId: Long,
    val old: String?,
    val new: String?
)
