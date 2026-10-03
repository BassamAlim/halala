package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import bassamalim.halala.core.enums.TransactionKind

/**
 * A transaction you said went toward a savings goal (or came out of it, [withdrawn]): money
 * put somewhere the ledger has no account for, like a broker (Al Rajhi Capital). Its amount and
 * day are the transaction's. A plain transaction marked this way stops counting as spending or
 * income: its kind becomes a savings one, and [kindBefore] is what it goes back to.
 */
@Entity(
    tableName = "goal_contributions",
    foreignKeys = [
        ForeignKey(entity = Transaction::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SavingsGoal::class, parentColumns = ["id"], childColumns = ["goalId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["uid"], unique = true), Index(value = ["transactionId"], unique = true), Index(value = ["goalId"])]
)
data class GoalContribution(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val goalId: Long,
    val transactionId: Long,
    val withdrawn: Boolean = false,
    /** Null for a move between your own accounts, whose kind is left alone. */
    val kindBefore: TransactionKind? = null
)
