package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.enums.AmountTone
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.models.TransactionItem
import bassamalim.halala.core.utils.accountLabel
import bassamalim.halala.core.utils.dayLabel
import bassamalim.halala.core.utils.initialOf
import java.time.LocalDate
import java.time.ZoneId

/** How a transaction's amount reads: moves and corrections are neither spending nor income. */
fun toneOf(detail: TransactionDetail): AmountTone = when {
    detail.isInternalTransfer || detail.transaction.kind == TransactionKind.ADJUSTMENT -> AmountTone.Internal
    detail.transaction.direction == Direction.CREDIT -> AmountTone.Income
    else -> AmountTone.Spending
}

/**
 * The amount as a row shows it: "−214.50", "+18,000.00", "5,000.00" for a move. The currency
 * is set beside it by the UI.
 */
fun signedAmount(detail: TransactionDetail, decimals: Boolean = true): String {
    val tx = detail.transaction
    val tone = toneOf(detail)
    val signed = if (tone == AmountTone.Spending) -tx.amountMinor else tx.amountMinor
    return Money.format(signed, tx.currency, decimals = decimals, showPlus = tone == AmountTone.Income)
}

/**
 * A move is titled by its two accounts, sending side first ("Salary → Awaeed"), whichever leg
 * is being shown.
 */
fun titleOf(detail: TransactionDetail): String {
    if (!detail.isInternalTransfer) return detail.transaction.title

    val here = detail.accountNickname
    val there = detail.counterpartNickname.orEmpty()
    return if (detail.isTransferInLeg) "$there → $here" else "$here → $there"
}

fun TransactionDetail.toItem(zone: ZoneId, today: LocalDate): TransactionItem {
    val date = transaction.occurredAt.atZone(zone).toLocalDate()
    val title = titleOf(this)

    return TransactionItem(
        id = transaction.id,
        title = title,
        kind = transaction.kind,
        accountLabel = accountLabel(institutionName, accountNickname),
        isMove = isInternalTransfer,
        amount = signedAmount(this),
        currency = transaction.currency,
        tone = toneOf(this),
        initial = initialOf(title),
        date = date,
        day = dayLabel(date, today),
        category = categoryName,
        auto = transaction.ruleId != null
    )
}

/**
 * What a feed shows: a move once, by its sending leg, unless the feed is narrowed to one account,
 * where that account's own leg is the one that belongs.
 */
fun feedOf(details: List<TransactionDetail>, accountId: Long? = null): List<TransactionDetail> =
    if (accountId == null) details.filterNot { it.isTransferInLeg }
    else details.filter { it.transaction.accountId == accountId }
