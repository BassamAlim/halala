package bassamalim.halala.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.LayoutDirection
import bassamalim.halala.core.ui.settle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import bassamalim.halala.R
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaTheme
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Radius
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/** One tab: its label and its stroke glyph. */
data class BottomNavItem(val label: String, @param:DrawableRes val icon: Int)

/**
 * The five fixed tabs: surface fill, a line on top, 22dp icons over 11sp labels. The current tab
 * is accent and the others muted; a short jade line slides along the top line to it. There is no indicator pill. What waits for you is counted inside the Inbox tab,
 * never as a badge here.
 */
@Composable
fun BottomNav(
    items: List<BottomNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val at by animateFloatAsState(selectedIndex.toFloat(), settle(), label = "tab")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(HalalaColors.Surface)
            .drawBehind {
                drawLine(
                    color = HalalaColors.Line,
                    start = Offset.Zero,
                    end = Offset(size.width, 0f),
                    strokeWidth = Sizes.border.toPx()
                )
                // The items share the width equally, so the current one's centre is known.
                val slot = size.width / items.size
                val centre = (at + 0.5f) * slot
                val x = if (layoutDirection == LayoutDirection.Rtl) size.width - centre else centre
                val half = INDICATOR.toPx() / 2
                drawLine(
                    color = HalalaColors.Accent,
                    start = Offset(x - half, 0f),
                    end = Offset(x + half, 0f),
                    strokeWidth = INDICATOR_HEIGHT.toPx(),
                    cap = StrokeCap.Round
                )
            }
            // The fill runs under the gesture bar; the items sit above it.
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(top = Insets.navTop, bottom = Insets.navBottom)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val color by animateColorAsState(if (selected) HalalaColors.Accent else HalalaColors.TextMuted, label = "tab colour")

            Column(
                modifier = Modifier
                    .weight(1f)
                    .sizeIn(minWidth = Insets.navItemMinWidth, minHeight = Sizes.touchTarget)
                    .semantics { this.selected = selected }
                    .clip(Radius.sm)
                    .clickable(role = Role.Tab) {
                        if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(index)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Icon(
                    painter = painterResource(item.icon),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(Sizes.icon)
                )
                Text(text = item.label, style = HalalaType.NavLabel, color = color)
            }
        }
    }
}

private val INDICATOR = Spacing.section
private val INDICATOR_HEIGHT = Spacing.xxs

@Preview
@Composable
private fun BottomNavPreview() = HalalaTheme {
    BottomNav(
        items = listOf(
            BottomNavItem("Home", R.drawable.ic_home),
            BottomNavItem("Activity", R.drawable.ic_activity),
            BottomNavItem("Inbox", R.drawable.ic_inbox),
            BottomNavItem("Plan", R.drawable.ic_plan),
            BottomNavItem("Wealth", R.drawable.ic_wealth)
        ),
        selectedIndex = 0,
        onSelect = {}
    )
}
