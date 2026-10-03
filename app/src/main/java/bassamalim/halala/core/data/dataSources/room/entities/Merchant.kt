package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import bassamalim.halala.core.enums.AliasMatch
import bassamalim.halala.core.enums.BusinessType
import bassamalim.halala.core.enums.IdentifiedBy
import kotlinx.serialization.Serializable

/**
 * A business, however its bank spells it: "ABC TRDG EST 1234" and "ABC TRADING" are one
 * merchant with two aliases. [name] starts as the first descriptor seen; identifying the
 * merchant may tidy it ("PANDA 1042" → "Panda") until you name it yourself ([namedByYou]).
 *
 * [businessType] is what the business is, as [identifiedBy] said, with the AI's [confidence]
 * (0–100). None of the three is set until the merchant is identified.
 */
@Serializable
@Entity(tableName = "merchants", indices = [Index(value = ["uid"], unique = true)])
data class Merchant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val name: String,
    val businessType: BusinessType? = null,
    val identifiedBy: IdentifiedBy? = null,
    val confidence: Int? = null,
    @ColumnInfo(defaultValue = "0")
    val namedByYou: Boolean = false,
    /**
     * Whether its automatic rule was made. A rule you delete stays deleted: it isn't made again
     * unless what the merchant is changes.
     */
    @ColumnInfo(defaultValue = "0")
    val autoRuled: Boolean = false,
    /** Looked up online once, when the AI wasn't sure: never again, whatever it found. */
    @ColumnInfo(defaultValue = "0")
    val searchedOnline: Boolean = false,
    /** The page the answer came from, when one did ("Found online"), and its title. */
    val webUrl: String? = null,
    val webTitle: String? = null
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
