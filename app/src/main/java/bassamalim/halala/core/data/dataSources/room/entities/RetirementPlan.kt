package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Your retirement plan: the year you were born, the age you retire at, the income you want a
 * month in today's money, the return and inflation you expect (exact decimal text), and the
 * savings goal that holds the pot ([goalId]; its target and date are the plan's). One row ([id] 1).
 */
@Entity(
    tableName = "retirement_plan",
    foreignKeys = [ForeignKey(entity = SavingsGoal::class, parentColumns = ["id"], childColumns = ["goalId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index(value = ["goalId"])]
)
data class RetirementPlan(
    @PrimaryKey val id: Long = 1,
    val goalId: Long?,
    val birthYear: Int,
    val retireAt: Int,
    val wantedMinor: Long,
    val returnPercent: String,
    val inflationPercent: String,
    val currency: String
)
