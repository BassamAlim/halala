package bassamalim.halala.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/**
 * The boards' line chart: one jade line over a faint fill, a dot at its last point, and labels
 * under it. [values] are minor units; scaling them to the canvas is drawing, not money.
 */
@Composable
fun LineChart(
    values: List<Long>,
    labels: List<String>,
    description: String,
    height: Dp,
    modifier: Modifier = Modifier,
    /** A second series, drawn dashed and muted under the first (what you put in, against what it grew to). */
    secondary: List<Long>? = null
) {
    if (values.size < 2) return
    val line = HalalaColors.Accent
    val grid = HalalaColors.Line
    val muted = HalalaColors.TextMuted
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = description }
        ) {
            val all = values + secondary.orEmpty()
            val min = all.min()
            val max = all.max().coerceAtLeast(min + 1)
            fun x(i: Int) = size.width * i / (values.size - 1)
            fun y(v: Long) = size.height - size.height * ((v - min).toFloat() / (max - min).toFloat())
            val stroke = Sizes.border.toPx() * 2

            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = Sizes.border.toPx())
            val path = Path().apply { values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
            val fill = Path().apply {
                addPath(path)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(fill, line.copy(alpha = 0.10f))
            secondary?.takeIf { it.size == values.size }?.let { second ->
                val dashed = Path().apply { second.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
                val dash = Spacing.xs.toPx()
                drawPath(dashed, muted, style = Stroke(width = stroke * 3 / 4, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))))
            }
            drawPath(path, line, style = Stroke(width = stroke))
            drawCircle(line, radius = Spacing.xs.toPx(), center = Offset(x(values.size - 1), y(values.last())))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach { Text(text = it, style = HalalaType.Caption, color = HalalaColors.TextMuted) }
        }
    }
}
