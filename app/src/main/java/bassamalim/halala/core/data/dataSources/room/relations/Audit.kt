package bassamalim.halala.core.data.dataSources.room.relations

import androidx.room.Embedded
import bassamalim.halala.core.data.dataSources.room.entities.AuditBatch
import bassamalim.halala.core.enums.ExpenseType
import kotlinx.serialization.Serializable

/** A batch with how many transactions it re-filed. */
data class BatchWithCount(
    @Embedded val batch: AuditBatch,
    val transactions: Int
)

/** How one transaction is filed: the three columns a rule or you may set. */
@Serializable
data class Filing(
    val categoryId: Long? = null,
    val expenseType: ExpenseType? = null,
    val ruleId: Long? = null
)

data class FilingRow(val id: Long, @Embedded val filing: Filing)
