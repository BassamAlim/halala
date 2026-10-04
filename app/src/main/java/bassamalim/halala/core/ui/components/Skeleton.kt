package bassamalim.halala.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaTheme
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Radius
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/** The loading fill: the card's tone with a surface-2 band sweeping across it. Clip before it. */
fun Modifier.shimmer(): Modifier = composed {
    val shift by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1200, easing = LinearEasing)),
        label = "shimmer"
    )
    drawBehind {
        drawRect(
            Brush.horizontalGradient(
                colors = listOf(HalalaColors.Card, HalalaColors.Surface2, HalalaColors.Card),
                startX = size.width * shift,
                endX = size.width * (shift + 1f)
            )
        )
    }
}

/** What a screen shows while its data loads: a card's shape, then rows. */
@Composable
fun Skeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().clearAndSetSemantics {}) {
        Box(
            Modifier
                .padding(top = Spacing.card, bottom = Spacing.sm)
                .fillMaxWidth()
                .height(Sizes.skeletonCard)
                .clip(Radius.lg)
                .shimmer()
        )
        SkeletonRows()
    }
}

/** Transaction rows still loading: an avatar and two lines each. */
@Composable
fun SkeletonRows(modifier: Modifier = Modifier, count: Int = 5) {
    Column(modifier.fillMaxWidth().clearAndSetSemantics {}) {
        repeat(count) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Insets.row),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Box(Modifier.size(Sizes.avatar).clip(Radius.sm).shimmer())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Box(Modifier.fillMaxWidth(0.6f).height(Spacing.md).clip(Radius.xs).shimmer())
                    Box(Modifier.fillMaxWidth(0.35f).height(Spacing.sm).clip(Radius.xs).shimmer())
                }
            }
        }
    }
}

@Preview
@Composable
private fun SkeletonPreview() {
    HalalaTheme { Skeleton() }
}
