package bassamalim.halala.core.data.dataSources.room.relations

import bassamalim.halala.core.enums.LoanEventType
import bassamalim.halala.core.enums.TransactionKind
import java.time.Instant

/**
 * What happened to a loan, as shown: the amount and time are its transfer's when it has one
 * (and its own otherwise), with the account the transfer is on.
 */
data class LoanEventRow(
    val id: Long,
    val loanId: Long,
    val type: LoanEventType,
    val transactionId: Long?,
    val amountMinor: Long,
    val at: Instant,
    val kind: TransactionKind?,
    val accountNickname: String?,
    val institutionName: String?
)
