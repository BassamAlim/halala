package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * A savings goal: [targetMinor] by [targetDate], held in the accounts [accountIds] (their
 * balances are what is saved; nothing is set aside inside Halala).
 */
@Entity(tableName = "savings_goals", indices = [Index(value = ["uid"], unique = true)])
data class SavingsGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val name: String,
    val targetMinor: Long,
    val currency: String,
    val targetDate: LocalDate? = null,
    /** The accounts it is saved in. */
    val accountIds: List<Long>,
    val createdAt: Instant
)
