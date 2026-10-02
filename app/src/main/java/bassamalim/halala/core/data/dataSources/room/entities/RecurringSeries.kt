package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import bassamalim.halala.core.enums.CadenceUnit
import bassamalim.halala.core.enums.RecurringKind
import bassamalim.halala.core.enums.SeriesStatus
import java.time.Instant
import java.time.LocalDate

/**
 * A subscription, bill or planned payment: [amountMinor] every [every] [unit], due on [anchor]
 * and every cadence from it. Its charges are the transactions of its [merchantId] (or, for
 * money to someone, its [personId]); each one that arrives moves the next due date on. One with
 * neither is assumed paid when its day passes.
 *
 * [amountMinor] is the price you know: a charge above it is a price rise to tell you about.
 */
@Entity(
    tableName = "recurring_series",
    foreignKeys = [
        ForeignKey(
            entity = Merchant::class,
            parentColumns = ["id"],
            childColumns = ["merchantId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["uid"], unique = true), Index(value = ["merchantId"]), Index(value = ["personId"])]
)
data class RecurringSeries(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val kind: RecurringKind,
    val name: String,
    val merchantId: Long? = null,
    val personId: Long? = null,
    /** Integer minor units, > 0. */
    val amountMinor: Long,
    val currency: String,
    val every: Int = 1,
    val unit: CadenceUnit,
    val anchor: LocalDate,
    val autoRenew: Boolean = false,
    /** The last day it runs (a 12-month contract); null when it runs on. */
    val endsOn: LocalDate? = null,
    /** Days before it is due to remind you; null for no reminder. */
    val reminderDays: Int? = null,
    /** You asked to be reminded to cancel it before it renews. */
    val cancelReminder: Boolean = false,
    val status: SeriesStatus,
    val createdAt: Instant
)
