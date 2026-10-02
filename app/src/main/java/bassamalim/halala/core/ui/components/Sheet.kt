package bassamalim.halala.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

/** The one bottom sheet: surface fill, rounded where it rises from, screen padding inside. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HalalaSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
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
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            content = content
        )
    }
}

/** One choice out of several, in a sheet: a category, a type. Picking is the answer; there is no Save. */
@Composable
fun <T> ChoiceSheet(
    title: String,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit
) {
    HalalaSheet(onDismiss) {
        Text(text = title, style = HalalaType.Title)
        // A long list (every business type) scrolls under its title.
        ChoiceChips(
            options = options,
            selected = selected,
            label = label,
            onSelect = onPick,
            modifier = Modifier.verticalScroll(rememberScrollState())
        )
    }
}
