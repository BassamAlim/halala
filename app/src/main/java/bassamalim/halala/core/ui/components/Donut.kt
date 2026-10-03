package bassamalim.halala.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import bassamalim.halala.core.ui.settle
import bassamalim.halala.core.ui.theme.Sizes

private const val GAP_DEGREES = 2f

/**
 * A ring of [fractions] (shares of the whole, biggest first) in [colors], clockwise from the
 * top, a hairline gap between them; [center] sits in the hole. It sweeps in once, like a bar.
 */
@Composable
fun Donut(fractions: List<Float>, colors: List<Color>, description: String, modifier: Modifier = Modifier, center: @Composable () -> Unit = {}) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(Unit) { sweep.animateTo(1f, settle()) }
    Box(modifier.size(Sizes.donut).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(Sizes.donut)) {
            val ring = Sizes.donutRing.toPx()
            val gap = if (fractions.size > 1) GAP_DEGREES else 0f
            var start = -90f
            fractions.forEachIndexed { i, fraction ->
                val angle = 360f * fraction * sweep.value
                if (angle > gap) drawArc(
                    color = colors[i % colors.size],
                    startAngle = start + gap / 2,
                    sweepAngle = angle - gap,
                    useCenter = false,
                    topLeft = Offset(ring / 2, ring / 2),
                    size = Size(size.width - ring, size.height - ring),
                    style = Stroke(width = ring)
                )
                start += angle
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { center() }
    }
}
