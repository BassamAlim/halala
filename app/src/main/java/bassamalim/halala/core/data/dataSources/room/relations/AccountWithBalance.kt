package bassamalim.halala.core.data.dataSources.room.relations

import androidx.room.Embedded
import bassamalim.halala.core.data.dataSources.room.entities.Account

/** An account with its bank's name and its balance: opening balance plus every transaction. */
data class AccountWithBalance(
    @Embedded val account: Account,
    val institutionName: String?,
    val balanceMinor: Long,
    val transactionCount: Int
)
