package bassamalim.halala.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Radius
import bassamalim.halala.core.ui.theme.Spacing

/**
 * A destructive action's confirmation, in a sheet: what will happen, a way out, and the confirm
 * button, which is secondary with state-over text (never a loud fill).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmSheet(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = HalalaColors.Surface,
        contentColor = HalalaColors.Text,
        scrimColor = HalalaColors.Scrim,
        // Rounded where it rises from, square where it meets the screen's edge.
        shape = Radius.lg.copy(bottomStart = CornerSize(0), bottomEnd = CornerSize(0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.section),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(text = title, style = HalalaType.Title)
            Text(text = body, style = HalalaType.Body, color = HalalaColors.TextMuted)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                HalalaButton(text = dismissLabel, onClick = onDismiss, modifier = Modifier.weight(1f))
                HalalaButton(
                    text = confirmLabel,
                    onClick = onConfirm,
                    destructive = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
