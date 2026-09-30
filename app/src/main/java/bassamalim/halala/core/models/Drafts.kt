package bassamalim.halala.core.models

import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import java.time.Instant

/** A new or edited account, before it is a row. */
data class AccountDraft(
    val institutionId: Long?,
    val nickname: String,
    val type: AccountType,
    val last4: String?,
    val currency: String,
    val openingBalanceMinor: Long
)

/**
 * One transaction on one account, before it is a row. There is no currency: a transaction is
 * always in its account's currency, and the repository fills it in.
 */
data class TransactionDraft(
    val accountId: Long,
    val direction: Direction,
    val amountMinor: Long,
    val occurredAt: Instant,
    val kind: TransactionKind,
    val title: String = "",
    val note: String = "",
    val source: TransactionSource = TransactionSource.MANUAL
)

/** A move between two of your own accounts: two legs, one pairing. */
data class TransferDraft(
    val fromAccountId: Long,
    val toAccountId: Long,
    val amountMinor: Long,
    val occurredAt: Instant,
    val kind: TransactionKind,
    val title: String = "",
    val note: String = ""
)
