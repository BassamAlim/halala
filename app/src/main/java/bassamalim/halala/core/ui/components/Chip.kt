package bassamalim.halala.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

    /** Light fill: a chosen option among several (forms, ranges). Neutral, so jade stays for actions. */
    On
}

/**
 * A 32dp pill in label text. When [onClick] is set its hit area is padded out to a touch
 * target, while the pill itself stays 32dp. A disabled chip keeps its size and is dimmed.
 */
@Composable
fun HalalaChip(
    label: String,
    modifier: Modifier = Modifier,
    style: ChipStyle = ChipStyle.Plain,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    // A chip changing style (a filter turned on) eases between its looks rather than snapping.
    val fill by animateColorAsState(
        when (style) {
            ChipStyle.Plain -> HalalaColors.Surface2
            ChipStyle.On -> HalalaColors.Text
            else -> HalalaColors.Accent.copy(alpha = 0f)
        },
        tween(CHIP_MS),
        label = "chip fill"
    )
    val edge by animateColorAsState(
        when (style) {
            ChipStyle.Outline -> HalalaColors.Line
            ChipStyle.Accent -> HalalaColors.Accent
            else -> HalalaColors.Line.copy(alpha = 0f)
        },
        tween(CHIP_MS),
        label = "chip edge"
    )
    val ink by animateColorAsState(
        when (style) {
            ChipStyle.On -> HalalaColors.Bg
            ChipStyle.Accent -> HalalaColors.Accent
            else -> HalalaColors.Text
        },
        tween(CHIP_MS),
        label = "chip ink"
    )
    val pill = @Composable {
        Box(
            modifier = Modifier
                .height(Sizes.chip)
                .clip(Radius.pill)
                .then(if (onClick != null) Modifier.indication(interactionSource, LocalIndication.current) else Modifier)
                .background(fill)
                .border(width = Sizes.border, color = edge, shape = Radius.pill)
                .padding(horizontal = Insets.chip),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = if (style == ChipStyle.On) HalalaType.Label.copy(fontWeight = FontWeight(600))
                else HalalaType.Label,
                color = ink,
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
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) { pill() }
    }
}

private const val CHIP_MS = 180

/** The small uppercase accent tag after a row title when the app filed it itself. */
@Composable
fun AutoBadge(label: String, modifier: Modifier = Modifier, color: Color = HalalaColors.Accent) {
    Text(
        text = label.uppercase(),
        style = HalalaType.Badge,
        color = color,
        modifier = modifier
            .border(Sizes.border, if (color == HalalaColors.Accent) color else HalalaColors.Line, Radius.xs)
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
