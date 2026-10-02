package bassamalim.halala.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/** One band: its label, amount as text, weight (minor units, for drawing only) and colour. */
data class SankeyBand(val label: String, val amount: String, val weight: Long, val color: Color)

private const val BAND_ALPHA = 0.16f
private const val FLOW_SHARE = 0.5f

/**
 * The Money flow board's Sankey: one source bar at the start, bands curving to a bar per
 * destination, each labelled with its name and amount. [sourceWeight] is at least the bands'
 * total; a node too small to read is drawn at [Sizes.sankeyNode].
 */
@Composable
fun Sankey(bands: List<SankeyBand>, sourceWeight: Long, sourceColor: Color, description: String, modifier: Modifier = Modifier) {
    if (bands.isEmpty()) return
    val total = maxOf(sourceWeight, bands.sumOf { it.weight }).coerceAtLeast(1)
    val gap = Spacing.sm
    val minNode = Sizes.sankeyNode
    // The source is as tall as the bands would be at a scale where the smallest still reads.
    val perUnit = bands.minOf { it.weight }.coerceAtLeast(1).let { smallest -> minNode.value / smallest.toFloat() }
    val sourceHeight = (total * perUnit).coerceIn(minNode.value * bands.size, Sizes.sankeyMax.value)
    val heights = bands.map { maxOf(minNode.value, sourceHeight * it.weight / total) }
    val tops = heights.runningFold(0f) { top, h -> top + h + gap.value }.dropLast(1)
    val height = maxOf(sourceHeight, tops.last() + heights.last()).dp
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    BoxWithConstraints(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        val flowEnd = maxWidth * FLOW_SHARE
        Canvas(Modifier.fillMaxSize()) {
            val bar = Sizes.sankeyBar.toPx()
            val end = flowEnd.toPx()
            val scaleY = sourceHeight.dp.toPx() / total
            scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f) {
                drawRoundRect(sourceColor, Offset(0f, 0f), Size(bar, sourceHeight.dp.toPx()), CornerRadius(bar / 4))
                var from = 0f
                bands.forEachIndexed { i, band ->
                    val a0 = from
                    val a1 = from + band.weight * scaleY
                    from = a1
                    val b0 = tops[i].dp.toPx()
                    val b1 = b0 + heights[i].dp.toPx()
                    val mid = (bar + end) / 2
                    val path = Path().apply {
                        moveTo(bar, a0)
                        cubicTo(mid, a0, mid, b0, end, b0)
                        lineTo(end, b1)
                        cubicTo(mid, b1, mid, a1, bar, a1)
                        close()
                    }
                    drawPath(path, band.color.copy(alpha = BAND_ALPHA))
                    drawRoundRect(band.color, Offset(end, b0), Size(bar, b1 - b0), CornerRadius(bar / 4))
                }
            }
        }
        bands.forEachIndexed { i, band ->
            Box(
                Modifier
                    .offset(y = tops[i].dp)
                    .height(heights[i].dp.coerceAtLeast(minNode))
                    .fillMaxWidth()
                    .padding(start = flowEnd + Sizes.sankeyBar + Spacing.sm),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(text = band.label, style = HalalaType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(text = band.amount, style = HalalaNumbers.Meta, color = HalalaColors.TextMuted)
                }
            }
        }
    }
}
