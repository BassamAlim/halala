package bassamalim.halala.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import bassamalim.halala.core.ui.settle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import bassamalim.halala.core.domain.BudgetState
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaTheme
import bassamalim.halala.core.ui.theme.Radius
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/** The fill a budget state is shown in, everywhere: the balance card and every bar. */
val BudgetState.color: Color
    get() = when (this) {
        BudgetState.OK -> HalalaColors.StateOk
        BudgetState.WARN -> HalalaColors.StateWarn
        BudgetState.OVER -> HalalaColors.StateOver
    }

/**
 * A 4dp surface-2 track with a fill in the budget state's colour. Always shown beside the
 * numbers it stands for; never the only signal.
 */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, state: BudgetState = BudgetState.OK) {
    // Fills from empty when it first shows, and glides when the figure moves.
    val shown = remember { Animatable(0f) }
    LaunchedEffect(progress) { shown.animateTo(progress.coerceIn(0f, 1f), settle()) }
    val fill by animateColorAsState(state.color, label = "bar fill")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Sizes.progress)
            .clip(Radius.bar)
            .background(HalalaColors.Surface2)
    ) {
        Box(
            Modifier
                .fillMaxWidth(shown.value)
                .fillMaxHeight()
                .clip(Radius.bar)
                .background(fill)
        )
    }
}

@Preview
@Composable
private fun ProgressBarPreview() = HalalaTheme {
    Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.card)) {
        ProgressBar(0.45f)
        ProgressBar(0.86f, state = BudgetState.WARN)
        ProgressBar(1f, state = BudgetState.OVER)
    }
}
