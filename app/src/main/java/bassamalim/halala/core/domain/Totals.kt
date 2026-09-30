package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.enums.Direction

/** Money in and out over some transactions, in one currency, both as positive minor units. */
data class InOut(val inMinor: Long, val outMinor: Long)

/**
 * Income and spending in [currency]. Moves between your own accounts and wallet corrections
 * are neither, and amounts in other currencies aren't mixed in (that needs the SAR amount the
 * bank charged, which arrives with SMS parsing).
 */
fun inOut(transactions: List<TransactionDetail>, currency: String): InOut {
    val counted = transactions
        .filter { !it.isInternalTransfer }
        .map { it.transaction }
        .filter { it.currency == currency && it.kind.countsInTotals }

    return InOut(
        inMinor = Money.sum(counted.filter { it.direction == Direction.CREDIT }.map { it.amountMinor }),
        outMinor = Money.sum(counted.filter { it.direction == Direction.DEBIT }.map { it.amountMinor })
    )
}
