package bassamalim.halala.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaTheme
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Radius
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/** The four chip styles. */
enum class ChipStyle {
    /** Surface-2: a value, such as a kind on a transaction. */
    Plain,

    /** Line border: an unselected filter. */
    Outline,

    /** Accent outline: the active filter in a filter row. */
    Accent,

    /** Accent fill: the selected option in a segmented choice. */
    On
}

/**
 * A 28dp pill in caption text. When [onClick] is set its hit area is padded out to a touch
 * target, while the pill itself stays 28dp.
 */
@Composable
fun HalalaChip(
    label: String,
    modifier: Modifier = Modifier,
    style: ChipStyle = ChipStyle.Plain,
    onClick: (() -> Unit)? = null
) {
    val pill = @Composable {
        Box(
            modifier = Modifier
                .height(Sizes.chip)
                .clip(Radius.pill)
                .background(
                    when (style) {
                        ChipStyle.Plain -> HalalaColors.Surface2
                        ChipStyle.On -> HalalaColors.Accent
                        else -> Color.Transparent
                    }
                )
                .border(
                    width = Sizes.border,
                    color = when (style) {
                        ChipStyle.Outline -> HalalaColors.Line
                        ChipStyle.Accent -> HalalaColors.Accent
                        else -> Color.Transparent
                    },
                    shape = Radius.pill
                )
                .padding(horizontal = Insets.chip),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = if (style == ChipStyle.On) HalalaType.Caption.copy(fontWeight = FontWeight(600))
                else HalalaType.Caption,
                color = when (style) {
                    ChipStyle.On -> HalalaColors.OnAccent
                    ChipStyle.Accent -> HalalaColors.Accent
                    else -> HalalaColors.Text
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (onClick == null) {
        Box(modifier) { pill() }
    } else {
        Box(
            modifier = modifier
                .heightIn(min = Sizes.touchTarget)
                .semantics { selected = style == ChipStyle.On || style == ChipStyle.Accent }
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center
        ) { pill() }
    }
}

/** The small uppercase accent tag after a row title when the app filed it itself. */
@Composable
fun AutoBadge(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label.uppercase(),
        style = HalalaType.Badge,
        color = HalalaColors.Accent,
        modifier = modifier
            .border(Sizes.border, HalalaColors.Accent, Radius.xs)
            .padding(horizontal = Insets.badgeX, vertical = Insets.badgeY)
    )
}

@Preview
@Composable
private fun ChipPreview() = HalalaTheme {
    Row(
        Modifier.padding(Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HalalaChip("1Y", style = ChipStyle.On, onClick = {})
        HalalaChip("Groceries")
        HalalaChip("All accounts", style = ChipStyle.Outline, onClick = {})
        HalalaChip("This cycle", style = ChipStyle.Accent, onClick = {})
        AutoBadge("Auto")
    }
}
