package bassamalim.halala.core.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import bassamalim.halala.R
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.Radius
import bassamalim.halala.core.ui.theme.Sizes

/**
 * The spec's global quick-add. The boards don't draw one, so it is the plainest accent object
 * the system allows: a flat jade square with the add glyph, no elevation.
 */
@Composable
fun QuickAddButton(contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(Sizes.fab),
        shape = Radius.lg,
        containerColor = HalalaColors.Accent,
        contentColor = HalalaColors.OnAccent,
        // Flat: the system has no shadows.
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = contentDescription,
            modifier = Modifier.size(Sizes.icon)
        )
    }
}
