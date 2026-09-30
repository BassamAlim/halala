package bassamalim.halala.core.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavBackStackEntry

val inFromBottom = { _: AnimatedContentTransitionScope<NavBackStackEntry> ->
    slideInVertically(initialOffsetY = { 500 }, animationSpec = tween(300)) +
            fadeIn(animationSpec = tween(200))
}

val outToBottom = { _: AnimatedContentTransitionScope<NavBackStackEntry> ->
    slideOutVertically(targetOffsetY = { -500 }, animationSpec = tween(300)) +
            fadeOut(animationSpec = tween(200))
}

val inFromTop = { _: AnimatedContentTransitionScope<NavBackStackEntry> ->
    slideInVertically(initialOffsetY = { -500 }, animationSpec = tween(300)) +
            fadeIn(animationSpec = tween(200))
}

val outToTop = { _: AnimatedContentTransitionScope<NavBackStackEntry> ->
    slideOutVertically(targetOffsetY = { 500 }, animationSpec = tween(300)) +
            fadeOut(animationSpec = tween(200))
}

val inFromRight = { _: AnimatedContentTransitionScope<NavBackStackEntry> ->
    slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) +
            fadeIn(animationSpec = tween(300))
}

val outToLeft = { _: AnimatedContentTransitionScope<NavBackStackEntry> ->
    slideOutHorizontally(targetOffsetX = { -500 }, animationSpec = tween(300)) +
            fadeOut(animationSpec = tween(300))
}

val inFromLeft = { _: AnimatedContentTransitionScope<NavBackStackEntry> ->
    slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) +
            fadeIn(animationSpec = tween(100))
}

val outToRight = { _: AnimatedContentTransitionScope<NavBackStackEntry> ->
    slideOutHorizontally(targetOffsetX = { 500 }, animationSpec = tween(300)) +
            fadeOut(animationSpec = tween(300))
}
