package bassamalim.halala.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaTheme
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Radius
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/**
 * The segmented control from the Activity board: a surface track with a line border, the chosen
 * segment raised on surface-2. Pass `Modifier.fillMaxWidth()` to spread the segments evenly.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false
) {
    Row(
        modifier = modifier
            .clip(Radius.sm)
            .background(HalalaColors.Surface)
            .border(Sizes.border, HalalaColors.Line, Radius.sm)
            .padding(Insets.segment)
            .selectableGroup()
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex

            Box(
                modifier = Modifier
                    .then(if (fill) Modifier.weight(1f) else Modifier)
                    .heightIn(min = SEGMENT_HEIGHT)
                    .clip(Radius.segment)
                    .background(if (selected) HalalaColors.Surface2 else Color.Transparent)
                    .semantics { this.selected = selected }
                    .clickable(role = Role.Tab, onClick = { onSelect(index) })
                    .padding(horizontal = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    style = if (selected) HalalaType.Label.copy(fontWeight = FontWeight(500)) else HalalaType.Label,
                    color = if (selected) HalalaColors.Text else HalalaColors.TextMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Track padding on both sides makes the whole control a touch target tall. */
private val SEGMENT_HEIGHT = Sizes.touchTarget - Insets.segment * 2

@Preview
@Composable
private fun SegmentedControlPreview() = HalalaTheme {
    SegmentedControl(
        options = listOf("Transactions", "Money flow"),
        selectedIndex = 0,
        onSelect = {},
        modifier = Modifier.padding(Spacing.screen)
    )
}
