package bassamalim.halala.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import bassamalim.halala.core.ui.theme.Spacing
import bassamalim.halala.core.ui.Emphasized
import bassamalim.halala.core.ui.settle
import kotlinx.coroutines.delay

/**
 * A figure, already formatted, followed by its currency, that comes alive: when it first shows
 * its characters rise into place one after another, and when it changes each character that
 * changed rolls to its new value like an odometer, up when it grows, down when it shrinks. It is
 * only ever moved, never worked out: the string is the ViewModel's.
 */
@Composable
fun RollingAmount(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    currency: String? = null,
    currencyStyle: TextStyle = style
) {
    val spoken = if (currency != null) "$text $currency" else text
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Row(Modifier.clipToBounds()) {
            // Keyed from the end, so the units stay put when the figure gains a digit.
            text.forEachIndexed { index, char ->
                key(text.length - index) { RollingChar(char, index, style, color) }
            }
        }
        if (currency != null && text.isNotEmpty()) CurrencyText(currency, currencyStyle, color)
    }
}

@Composable
private fun RollingChar(char: Char, index: Int, style: TextStyle, color: Color) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * STAGGER_MS)
        shown.animateTo(1f, settle())
    }

    AnimatedContent(
        targetState = char,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically(tween(ROLL_MS, easing = Emphasized)) { if (up) it else -it } + fadeIn(tween(ROLL_MS)))
                .togetherWith(slideOutVertically(tween(ROLL_MS, easing = Emphasized)) { if (up) -it else it } + fadeOut(tween(ROLL_MS)))
        },
        modifier = Modifier.graphicsLayer {
            alpha = shown.value
            translationY = (1f - shown.value) * size.height * RISE
        },
        label = "digit"
    ) { Text(text = it.toString(), style = style, color = color) }
}

private const val STAGGER_MS = 28L
private const val ROLL_MS = 380
/** How far below its place a character starts, against its height. */
private const val RISE = 0.7f

