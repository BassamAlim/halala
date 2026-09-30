package bassamalim.halala.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import bassamalim.halala.core.models.TransactionItem
import bassamalim.halala.core.ui.itemMeta
import bassamalim.halala.core.ui.itemTitle

/** A [TransactionRow] for a [TransactionItem], as Home and Activity both list them. */
@Composable
fun TransactionItemRow(
    item: TransactionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    divider: Boolean = false,
    withDay: Boolean = false
) {
    TransactionRow(
        title = itemTitle(item),
        meta = itemMeta(item, withDay),
        amount = item.amount,
        tone = item.tone,
        initial = item.initial,
        divider = divider,
        onClick = onClick,
        modifier = modifier
    )
}
