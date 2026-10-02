package bassamalim.halala.core.data.dataSources.room.relations

import androidx.room.Embedded
import bassamalim.halala.core.data.dataSources.room.entities.Merchant
import bassamalim.halala.core.data.dataSources.room.entities.MerchantAlias
import bassamalim.halala.core.enums.TransactionKind
import java.time.Instant

/** A merchant with how much of the ledger is theirs: its aliases and transactions. */
data class MerchantWithStats(
    @Embedded val merchant: Merchant,
    val aliases: Int,
    val transactions: Int,
    val lastAt: Instant?
)

/** An alias with how many transactions it names. */
data class AliasWithCount(
    @Embedded val alias: MerchantAlias,
    val transactions: Int
)

/** What resolving merchants reads of a transaction. */
data class KeyRow(
    val id: Long,
    val title: String,
    val merchantKey: String,
    val kind: TransactionKind,
    val occurredAt: Instant
)
