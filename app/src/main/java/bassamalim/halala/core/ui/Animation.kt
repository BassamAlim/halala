package bassamalim.halala.core.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import bassamalim.halala.core.ui.theme.HalalaColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The app's one easing: quick out of the gate, a long soft landing. */
val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** The spring for things that follow a choice: a tab's indicator, a segment, a bar's fill. */
fun <T> settle() = spring<T>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)

/*
 * A push: the new screen glides in a fifth of the width as it fades up over the old one, which
 * drifts the other way and fades. Offsets are in layout direction, so Arabic mirrors it.
 */

private const val DURATION = 360
private const val EXIT = 200

val inFromRight = { _: AnimatedContentTransitionScope<NavBackStackEntry> ->
    slideInHorizontally(tween(DURATION, easing = Emphasized)) { it / 5 } + fadeIn(tween(DURATION, easing = Emphasized))
}

val outToLeft = { _: AnimatedContentTransitionScope<NavBackStackEntry> ->
    slideOutHorizontally(tween(DURATION, easing = Emphasized)) { -it / 10 } + fadeOut(tween(EXIT))
}

val inFromLeft = { _: AnimatedContentTransitionScope<NavBackStackEntry> ->
    slideInHorizontally(tween(DURATION, easing = Emphasized)) { -it / 10 } + fadeIn(tween(DURATION, easing = Emphasized))
}

val outToRight = { _: AnimatedContentTransitionScope<NavBackStackEntry> ->
    slideOutHorizontally(tween(DURATION, easing = Emphasized)) { it / 5 } + fadeOut(tween(EXIT))
}

/** A screen's opaque ground, so a push never shows one screen through another. */
fun Modifier.ground(): Modifier = background(HalalaColors.Bg)

/**
 * How everything tappable answers a touch: it sinks a little (a few dp at most, however wide it
 * is) and is washed lighter, then springs back on release. It is the theme's indication, so
 * every `clickable` has it.
 */
object PressIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressNode(interactionSource)
    override fun equals(other: Any?) = other === this
    override fun hashCode() = 1
}

private class PressNode(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private val pressed = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            var down: Job? = null
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> down = launch {
                        pressed.animateTo(1f, tween(PRESS_MS, easing = Emphasized)) { invalidateDraw() }
                    }
                    // A quick tap still shows the whole press before it lets go.
                    is PressInteraction.Release, is PressInteraction.Cancel -> launch {
                        down?.join()
                        pressed.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)) { invalidateDraw() }
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val p = pressed.value
        if (p == 0f) {
            drawContent()
            return
        }
        val shrink = minOf(MAX_SHRINK, SINK.toPx() / size.width.coerceAtLeast(1f))
        scale(1f - shrink * p) { this@draw.drawContent() }
        drawRect(HalalaColors.Pressed, alpha = p)
    }
}

private const val PRESS_MS = 110
private const val MAX_SHRINK = 0.04f
private val SINK = 6.dp
