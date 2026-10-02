package bassamalim.halala.core.data.dataSources.room.relations

import androidx.room.Embedded
import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.enums.BusinessType
import bassamalim.halala.core.enums.IdentifiedBy

/**
 * A transaction with the names needed to show it, and, when it is one leg of a move between
 * your own accounts, the other leg and the account that leg is on.
 */
data class TransactionDetail(
    @Embedded val transaction: Transaction,
    val accountNickname: String,
    val institutionName: String?,
    val counterpartId: Long?,
    val counterpartAccountId: Long?,
    val counterpartNickname: String?,
    val counterpartInstitutionName: String?,
    /** The receiving leg of a pair: the feed shows a move once, by its sending leg. */
    val isTransferInLeg: Boolean,
    val categoryName: String? = null,
    /** The merchant its descriptor belongs to, when it names a business. */
    val merchantId: Long? = null,
    val merchantName: String? = null,
    /** What its merchant was identified as, by whom, and how sure: why an automatic rule filed it. */
    val merchantType: BusinessType? = null,
    val merchantIdentifiedBy: IdentifiedBy? = null,
    val merchantConfidence: Int? = null,
    /** The person a transfer's title names. */
    val personId: Long? = null,
    val personName: String? = null,
    /** What others owe of it, when it was split: their shares. */
    val sharedMinor: Long = 0
) {
    val isInternalTransfer get() = counterpartId != null

    /** What of it is yours: the amount less what others owe of a split bill. */
    val yourMinor get() = transaction.amountMinor - sharedMinor
}
