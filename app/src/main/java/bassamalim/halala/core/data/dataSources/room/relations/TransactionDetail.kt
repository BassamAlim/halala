package bassamalim.halala.core.data.dataSources.room.relations

import androidx.room.Embedded
import bassamalim.halala.core.data.dataSources.room.entities.Transaction

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
    val merchantName: String? = null
) {
    val isInternalTransfer get() = counterpartId != null
}
