package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import bassamalim.halala.core.enums.AliasMatch
import kotlinx.serialization.Serializable

/**
 * A business, however its bank spells it: "ABC TRDG EST 1234" and "ABC TRADING" are one
 * merchant with two aliases. [name] starts as the first descriptor seen and is yours to change.
 */
@Serializable
@Entity(tableName = "merchants", indices = [Index(value = ["uid"], unique = true)])
data class Merchant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val name: String
)

/**
 * One way a bank writes a merchant: an [aliasKey] (`Merchants.key`) belongs to exactly one merchant.
 * Transactions find their merchant through it, by their own `merchantKey`, so merging or
 * splitting merchants moves aliases and never rewrites a transaction. [descriptor] is the key
 * as a bank first wrote it, to show.
 */
@Serializable
@Entity(
    tableName = "merchant_aliases",
    foreignKeys = [
        ForeignKey(
            entity = Merchant::class,
            parentColumns = ["id"],
            childColumns = ["merchantId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["aliasKey"], unique = true), Index(value = ["merchantId"])]
)
data class MerchantAlias(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchantId: Long,
    val aliasKey: String,
    val descriptor: String,
    val matchedBy: AliasMatch
)
