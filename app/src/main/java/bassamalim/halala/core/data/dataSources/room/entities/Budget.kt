package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import bassamalim.halala.core.enums.BudgetScope
import bassamalim.halala.core.enums.ExpenseType
import java.time.Instant

/**
 * A limit on spending for each pay cycle: [amountMinor] for everything ([BudgetScope.TOTAL]), a
 * category, an expense type or a merchant. With [rollover], what was left unspent last cycle is
 * added to this one's.
 */
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Merchant::class, parentColumns = ["id"], childColumns = ["merchantId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["uid"], unique = true), Index(value = ["categoryId"]), Index(value = ["merchantId"])]
)
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val scope: BudgetScope,
    val categoryId: Long? = null,
    val expenseType: ExpenseType? = null,
    val merchantId: Long? = null,
    /** Integer minor units, > 0. */
    val amountMinor: Long,
    val currency: String,
    val rollover: Boolean = false,
    val createdAt: Instant
)
