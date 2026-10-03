package bassamalim.halala.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import bassamalim.halala.R
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.Radius
import bassamalim.halala.core.ui.theme.Sizes

/**
 * The spec's global quick-add. The boards don't draw one: a jade square with the add glyph, lit
 * from above. Flat: no shadow, no glow.
 */
@Composable
fun QuickAddButton(contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(Sizes.fab)
            .clip(Radius.lg)
            .semantics { this.contentDescription = contentDescription }
            .clickable(role = Role.Button, onClick = onClick)
            .background(FabFill)
            .border(Sizes.border, FabEdge, Radius.lg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = null,
            tint = HalalaColors.OnAccent,
            modifier = Modifier.size(Sizes.icon)
        )
    }
}

private val FabFill = Brush.verticalGradient(listOf(lerp(HalalaColors.Accent, Color.White, 0.14f), HalalaColors.Accent))
private val FabEdge = Brush.verticalGradient(listOf(HalalaColors.Sheen, Color.Transparent))
