package bassamalim.halala.core.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

/*
 * A push: the new screen slides in by the full width while the old one slides out by the full
 * width, both in the same time, so it looks the same on every phone. Offsets are in layout
 * direction, so Arabic mirrors it.
 */

private const val DURATION = 300

val inFromRight = { _: AnimatedContentTransitionScope<NavBackStackEntry> ->
    slideInHorizontally(tween(DURATION)) { it } + fadeIn(tween(DURATION))
}

val outToLeft = { _: AnimatedContentTransitionScope<NavBackStackEntry> ->
    slideOutHorizontally(tween(DURATION)) { -it } + fadeOut(tween(DURATION))
}

val inFromLeft = { _: AnimatedContentTransitionScope<NavBackStackEntry> ->
    slideInHorizontally(tween(DURATION)) { -it } + fadeIn(tween(DURATION))
}

val outToRight = { _: AnimatedContentTransitionScope<NavBackStackEntry> ->
    slideOutHorizontally(tween(DURATION)) { it } + fadeOut(tween(DURATION))
}
