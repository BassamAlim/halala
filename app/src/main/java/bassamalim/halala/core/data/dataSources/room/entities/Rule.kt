package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import bassamalim.halala.core.enums.ExpenseType
import bassamalim.halala.core.enums.RuleSource
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * The learning memory: when [conditions] hold, [actions] are applied. Both are stored as JSON,
 * so a new kind of condition or action is a new field, not a migration. How often a rule was
 * used isn't stored: it is counted from the transactions it filed.
 */
@Entity(tableName = "rules", indices = [Index(value = ["uid"], unique = true)])
data class Rule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val conditions: RuleConditions,
    val actions: RuleActions,
    val source: RuleSource,
    val enabled: Boolean = true,
    val createdAt: Instant
)

/** All of the conditions that are set must hold. */
@Serializable
data class RuleConditions(
    /** The merchant as it was written when the rule was made; compared by its merchant key. */
    val merchant: String? = null
)

@Serializable
data class RuleActions(
    val categoryId: Long,
    val expenseType: ExpenseType? = null
)
