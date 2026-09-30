package bassamalim.halala.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.selectableGroup
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/** One tab: its label and its stroke glyph. */
data class BottomNavItem(val label: String, @param:DrawableRes val icon: Int)

/**
 * The five fixed tabs: surface fill, a line on top, 22dp icons over 11sp labels. The current
 * tab is accent and the others muted, with no indicator pill. The review count lives on Home,
 * never as a badge here.
 */
@Composable
fun BottomNav(
    items: List<BottomNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
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
            }
            .padding(top = Insets.navTop, bottom = Insets.navBottom)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val color = if (selected) HalalaColors.Accent else HalalaColors.TextMuted

            Column(
                modifier = Modifier
                    .sizeIn(minWidth = Insets.navItemMinWidth, minHeight = Sizes.touchTarget)
                    .semantics { this.selected = selected }
                    .clickable(role = Role.Tab, onClick = { onSelect(index) }),
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

@Preview
@Composable
private fun BottomNavPreview() = HalalaTheme {
    BottomNav(
        items = listOf(
            BottomNavItem("Home", R.drawable.ic_home),
            BottomNavItem("Activity", R.drawable.ic_activity),
            BottomNavItem("Plan", R.drawable.ic_plan),
            BottomNavItem("Wealth", R.drawable.ic_wealth),
            BottomNavItem("Assistant", R.drawable.ic_assistant)
        ),
        selectedIndex = 0,
        onSelect = {}
    )
}
