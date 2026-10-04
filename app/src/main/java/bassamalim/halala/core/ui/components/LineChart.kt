package bassamalim.halala.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * The boards' line chart: one jade line over a faint fill, a dot at its last point, and labels
 * under it. [values] are minor units; scaling them to the canvas is drawing, not money.
 * Touching a point shows its [tips] entry (when, and the figure already formatted) in place of
 * the labels; touching it again, or lifting off a drag, keeps it until the next touch.
 */
@Composable
fun LineChart(
    values: List<Long>,
    labels: List<String>,
    description: String,
    height: Dp,
    modifier: Modifier = Modifier,
    /** A second series, drawn dashed and muted under the first (what you put in, against what it grew to). */
    secondary: List<Long>? = null,
    tips: List<Pair<String, String>>? = null
) {
    if (values.size < 2) return
    val picked = remember(values.size) { mutableStateOf<Int?>(null) }
    val line = HalalaColors.Accent
    val grid = HalalaColors.Line
    val muted = HalalaColors.TextMuted
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = description }
                .then(if (tips?.size == values.size) Modifier.scrub(values.size, picked) else Modifier)
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
            picked.value?.let { drawPick(Offset(x(it), y(values[it]))) }
        }
        val at = picked.value
        if (at != null && tips != null) ChartTip(tips[at], at.toFloat() / (values.size - 1))
        else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach { Text(text = it, style = HalalaType.Caption, color = HalalaColors.TextMuted) }
        }
    }
}

/**
 * Picks the nearest of [count] evenly spaced points under a touch, following a drag. Lifting
 * keeps it; a tap on the point already picked lets it go.
 */
internal fun Modifier.scrub(count: Int, picked: MutableState<Int?>) = pointerInput(count) {
    fun at(x: Float) = (x / size.width * (count - 1)).roundToInt().coerceIn(0, count - 1)
    awaitEachGesture {
        val down = awaitFirstDown()
        val before = picked.value
        val first = at(down.position.x)
        picked.value = first
        var moved = false
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            val now = at(change.position.x)
            if (now != first) moved = true
            picked.value = now
        }
        if (!moved && before == first) picked.value = null
    }
}

/** The picked point: a hairline down the chart and a ringed dot on the line. */
internal fun DrawScope.drawPick(point: Offset) {
    drawLine(HalalaColors.TextMuted, Offset(point.x, 0f), Offset(point.x, size.height), strokeWidth = Sizes.border.toPx())
    drawCircle(HalalaColors.Bg, radius = Spacing.sm.toPx(), center = point)
    drawCircle(HalalaColors.Accent, radius = Spacing.xs.toPx(), center = point)
}

/** When and how much, under the picked point (at [fraction] across), kept inside the chart's width. */
@Composable
internal fun ChartTip(tip: Pair<String, String>, fraction: Float) {
    Row(
        modifier = Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0))
            layout(constraints.maxWidth, placeable.height) {
                val x = (constraints.maxWidth * fraction - placeable.width / 2f).roundToInt()
                placeable.place(x.coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0)), 0)
            }
        },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(text = tip.first, style = HalalaType.Caption, color = HalalaColors.TextMuted)
        Text(text = tip.second, style = HalalaNumbers.Meta, color = HalalaColors.Text)
    }
}
