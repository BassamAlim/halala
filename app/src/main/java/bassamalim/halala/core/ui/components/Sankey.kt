package bassamalim.halala.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import bassamalim.halala.core.ui.settle
import bassamalim.halala.core.ui.theme.Sizes

/** One part at an end of the Sankey: [key] names it for selection; [weight] is minor units, for drawing only. */
data class SankeyBand(val key: String, val weight: Long, val color: Color)

private const val BAND_ALPHA = 0.22f
private const val BAND_CHOSEN = 0.5f
private const val BAND_DIMMED = 0.07f
private const val NODE_DIMMED = 0.3f

/**
 * Money flow's Sankey, top to bottom so it has the phone's height: where the money came from
 * ([sources], a row of bars along the top) runs into the account (a bar across the middle), and
 * on to where it went ([uses], a row along the bottom). Both ends sum to the same. Tapping a
 * part, or the band running from it, chooses it ([onSelect] with its key): it is drawn full and
 * the rest dimmed. The words are beside it, in the lists that name each part.
 */
@Composable
fun Sankey(
    sources: List<SankeyBand>,
    uses: List<SankeyBand>,
    middleColor: Color,
    selected: String?,
    onSelect: (String) -> Unit,
    description: String,
    modifier: Modifier = Modifier
) {
    if (sources.isEmpty() && uses.isEmpty()) return
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptics = LocalHapticFeedback.current
    // Settles in: the bands grow out of the account.
    val grown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grown.animateTo(1f, settle()) }

    val bar = with(density) { Sizes.sankeyBar.toPx() }
    val flowIn = with(density) { Sizes.sankeyFlowIn.toPx() }
    val flowOut = with(density) { Sizes.sankeyFlowOut.toPx() }
    val gap = with(density) { Sizes.sankeyGap.toPx() }
    val least = with(density) { Sizes.sankeyMin.toPx() }
    val height = Sizes.sankeyBar * 3 + Sizes.sankeyFlowIn + Sizes.sankeyFlowOut

    // The tiers, top to bottom.
    val midTop = bar + flowIn
    val midBottom = midTop + bar
    val usesTop = midBottom + flowOut

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .pointerInput(sources, uses) {
                detectTapGestures { tap ->
                    val width = size.width.toFloat()
                    val x = if (rtl) width - tap.x else tap.x
                    val top = tap.y < (midTop + midBottom) / 2
                    val bands = if (top) sources else uses
                    val ends = spread(bands, width, gap, least)
                    val joins = joined(bands, width)
                    // How far along the band the tap is, from its end (0) to the account (1).
                    val t = if (top) ((tap.y - bar) / flowIn).coerceIn(0f, 1f)
                    else ((usesTop - tap.y) / flowOut).coerceIn(0f, 1f)
                    val hit = bands.indices.firstOrNull { i ->
                        val left = lerp(ends[i].first, joins[i].first, t) - gap / 2
                        val right = lerp(ends[i].second, joins[i].second, t) + gap / 2
                        x in left..right
                    }
                    if (hit != null) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(bands[hit].key)
                    }
                }
            }
    ) {
        val width = size.width
        scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f) {
            drawRoundRect(middleColor, Offset(0f, midTop), Size(width, bar), CornerRadius(bar / 2))
            tier(sources, width, gap, least, selected, grown.value, endTop = 0f, endBottom = bar, joinY = midTop, fromEnd = true)
            tier(uses, width, gap, least, selected, grown.value, endTop = usesTop, endBottom = usesTop + bar, joinY = midBottom, fromEnd = false)
        }
    }
}

/** One end's bars and the bands from them to the account. */
private fun DrawScope.tier(
    bands: List<SankeyBand>,
    width: Float,
    gap: Float,
    least: Float,
    selected: String?,
    grown: Float,
    endTop: Float,
    endBottom: Float,
    joinY: Float,
    fromEnd: Boolean
) {
    val ends = spread(bands, width, gap, least)
    val joins = joined(bands, width)
    val bar = endBottom - endTop
    // The band leaves the bar at its inner edge and meets the account.
    val endY = if (fromEnd) endBottom else endTop
    val reach = endY + (joinY - endY) * grown
    val mid = (endY + reach) / 2
    bands.forEachIndexed { i, band ->
        val chosen = selected == null || selected == band.key
        val (x0, x1) = ends[i]
        val (m0, m1) = joins[i]
        val path = Path().apply {
            moveTo(x0, endY)
            cubicTo(x0, mid, m0, mid, m0, reach)
            lineTo(m1, reach)
            cubicTo(m1, mid, x1, mid, x1, endY)
            close()
        }
        val alpha = when {
            selected == null -> BAND_ALPHA
            chosen -> BAND_CHOSEN
            else -> BAND_DIMMED
        }
        drawPath(path, band.color.copy(alpha = alpha))
        drawRoundRect(
            band.color.copy(alpha = if (chosen) 1f else NODE_DIMMED),
            Offset(x0, endTop),
            Size(x1 - x0, bar),
            CornerRadius(minOf(bar, x1 - x0) / 2)
        )
    }
}

/**
 * Where each part's bar sits along an end (start and end, px): apart by [gap], each at least
 * [least] wide, the rest of the width shared by weight.
 */
private fun spread(bands: List<SankeyBand>, width: Float, gap: Float, least: Float): List<Pair<Float, Float>> {
    if (bands.isEmpty()) return emptyList()
    val total = bands.sumOf { it.weight }.coerceAtLeast(1)
    val room = (width - gap * (bands.size - 1) - least * bands.size).coerceAtLeast(0f)
    var x = 0f
    return bands.map { band ->
        val w = least + room * band.weight / total
        (x to x + w).also { x += w + gap }
    }
}

/** Where each part's band meets the account: side by side across its whole width, by weight. */
private fun joined(bands: List<SankeyBand>, width: Float): List<Pair<Float, Float>> {
    val total = bands.sumOf { it.weight }.coerceAtLeast(1)
    var x = 0f
    return bands.map { band ->
        val w = width * band.weight / total
        (x to x + w).also { x += w }
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
