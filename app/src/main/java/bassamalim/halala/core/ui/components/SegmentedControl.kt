package bassamalim.halala.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import bassamalim.halala.core.ui.settle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaTheme
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Radius
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/**
 * The segmented control from the Activity board: a surface track with a line border, the chosen
 * segment raised on surface-2, which slides to the one you choose. Pass `Modifier.fillMaxWidth()`
 * to spread the segments evenly.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false
) {
    val haptics = LocalHapticFeedback.current
    // Where each segment sits (start and width, px), measured, so the thumb can slide between them.
    val bounds = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val start = remember { Animatable(0f) }
    val width = remember { Animatable(0f) }
    val target = bounds[selectedIndex]
    LaunchedEffect(target) {
        val (s, w) = target ?: return@LaunchedEffect
        if (width.value == 0f) {
            start.snapTo(s)
            width.snapTo(w)
        } else coroutineScope {
            launch { start.animateTo(s, settle()) }
            launch { width.animateTo(w, settle()) }
        }
    }

    Row(
        modifier = modifier
            .clip(Radius.sm)
            .background(HalalaColors.Surface)
            .border(Sizes.border, HalalaColors.Line, Radius.sm)
            .padding(Insets.segment)
            .drawBehind {
                if (width.value == 0f) return@drawBehind
                drawRoundRect(
                    color = HalalaColors.Surface2,
                    topLeft = Offset(start.value, 0f),
                    size = Size(width.value, size.height),
                    cornerRadius = CornerRadius(SEGMENT_RADIUS.toPx())
                )
            }
            .selectableGroup()
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val ink by animateColorAsState(if (selected) HalalaColors.Text else HalalaColors.TextMuted, label = "segment ink")

            Box(
                modifier = Modifier
                    .then(if (fill) Modifier.weight(1f) else Modifier)
                    .heightIn(min = SEGMENT_HEIGHT)
                    .onPlaced { bounds[index] = it.positionInParent().x to it.size.width.toFloat() }
                    .clip(Radius.segment)
                    .semantics { this.selected = selected }
                    .clickable(role = Role.Tab) {
                        if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(index)
                    }
                    .padding(horizontal = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    style = if (selected) HalalaType.Label.copy(fontWeight = FontWeight(500)) else HalalaType.Label,
                    color = ink,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/** [Radius.segment]'s size, for the thumb drawn behind the segments. */
private val SEGMENT_RADIUS = Radius.smSize - Insets.segment

/** Track padding on both sides makes the whole control a touch target tall. */
private val SEGMENT_HEIGHT = Sizes.touchTarget - Insets.segment * 2

@Preview
@Composable
private fun SegmentedControlPreview() = HalalaTheme {
    SegmentedControl(
        options = listOf("Transactions", "Money flow"),
        selectedIndex = 0,
        onSelect = {},
        modifier = Modifier.padding(Spacing.screen)
    )
}
