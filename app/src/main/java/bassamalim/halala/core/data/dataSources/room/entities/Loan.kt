package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import bassamalim.halala.core.enums.LoanDirection
import bassamalim.halala.core.enums.LoanEventType
import java.time.Instant
import java.time.LocalDate

/**
 * Money lent to or borrowed from a person, or their share of a bill you split. What it amounts to is its [LoanEvent]s: what was
 * lent, less what was repaid and forgiven, is what is still owed. Always in one [currency].
 */
@Entity(
    tableName = "loans",
    foreignKeys = [
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Transaction::class,
            parentColumns = ["id"],
            childColumns = ["splitOf"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["uid"], unique = true), Index(value = ["personId"]), Index(value = ["splitOf"])]
)
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val personId: Long,
    val direction: LoanDirection,
    val currency: String,
    /** When it should be paid back, if you said. */
    val dueOn: LocalDate? = null,
    val createdAt: Instant,
    /**
     * The purchase this is a share of, when a bill was split: what they owe you is their share
     * (an event without a transfer), and only the rest of the purchase is your spending.
     */
    val splitOf: Long? = null
)

/**
 * One thing that happened to a loan. One with a [transactionId] is that transfer, and its amount
 * and time are the transfer's (so editing the transfer never leaves the loan behind); one
 * without (forgiving what is left) carries its own [amountMinor] and [at].
 */
@Entity(
    tableName = "loan_events",
    foreignKeys = [
        ForeignKey(
            entity = Loan::class,
            parentColumns = ["id"],
            childColumns = ["loanId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Transaction::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uid"], unique = true),
        Index(value = ["loanId"]),
        // A transfer is part of one loan at most.
        Index(value = ["transactionId"], unique = true)
    ]
)
data class LoanEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val loanId: Long,
    val type: LoanEventType,
    val transactionId: Long? = null,
    /** Only without a transaction. Integer minor units, > 0. */
    val amountMinor: Long? = null,
    val at: Instant? = null
)
