package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import bassamalim.halala.core.enums.MaturityChoice
import java.time.LocalDate

/**
 * One term deposit (an Awaeed): the bank opens a numberless account for each, so each is kept
 * on its own rather than as an account of yours. Its amount and the day it started are those of
 * [transactionId], the leg that arrived in the bank's holding account, so editing the transfer
 * never leaves the deposit behind. [goalId] is what it is for.
 */
@Entity(
    tableName = "deposits",
    foreignKeys = [
        ForeignKey(entity = Transaction::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SavingsGoal::class, parentColumns = ["id"], childColumns = ["goalId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index(value = ["uid"], unique = true), Index(value = ["transactionId"], unique = true), Index(value = ["goalId"])]
)
data class Deposit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val transactionId: Long,
    val goalId: Long? = null,
    /** Exact decimal text, a year; the SMS doesn't say, so it is yours to give. */
    val ratePercent: String? = null,
    val tenorMonths: Int? = null,
    val maturityChoice: MaturityChoice? = null,
    /** The day it was paid out; null while the money is still in it. */
    val closedOn: LocalDate? = null
)
