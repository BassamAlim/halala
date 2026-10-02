package bassamalim.halala.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Spacing

/**
 * A question in a sheet: what will happen, a way out, and the confirm button. A [destructive]
 * confirm is secondary with state-over text (never a loud fill); any other is the primary.
 */
@Composable
fun ConfirmSheet(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
    /** The way-out button, when it does more than close the sheet. */
    onDismissClick: () -> Unit = onDismiss
) {
    HalalaSheet(onDismiss) {
        Text(text = title, style = HalalaType.Title)
        Text(text = body, style = HalalaType.Body, color = HalalaColors.TextMuted)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            HalalaButton(text = dismissLabel, onClick = onDismissClick, modifier = Modifier.weight(1f))
            HalalaButton(
                text = confirmLabel,
                onClick = onConfirm,
                kind = if (destructive) ButtonKind.Secondary else ButtonKind.Primary,
                destructive = destructive,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
