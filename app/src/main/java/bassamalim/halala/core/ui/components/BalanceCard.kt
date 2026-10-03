package bassamalim.halala.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.LayoutDirection
import bassamalim.halala.core.ui.settle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import bassamalim.halala.core.domain.BudgetState
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaTheme
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Radius
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/**
 * The one loud element on Home: spending this pay cycle against its budget. The fill follows
 * [state] and everything on it is on-accent. One per screen; never reuse the fill elsewhere.
 *
 * The consumer provides every string already formatted: [overline] ("Spent this pay cycle"),
 * [status] ("On track"), [spent] ("6,240"), [footStart] ("of 9,000 · 12 days left") and
 * [footEnd] ("End ≈ 4,180").
 */
@Composable
fun BalanceCard(
    overline: String,
    status: String,
    spent: String,
    currency: String,
    progress: Float,
    state: BudgetState,
    footStart: String,
    footEnd: String,
    modifier: Modifier = Modifier
) {
    // The state's colour eases across when it changes, and the bar fills from empty on arrival.
    val fill by animateColorAsState(state.color, tween(FADE_MS), label = "balance fill")
    val shown = remember { Animatable(0f) }
    LaunchedEffect(progress) { shown.animateTo(progress.coerceIn(0f, 1f), settle()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.lg)
            .drawBehind {
                // The fill deepens toward the bottom end, and the coin's rings (the mark's) sit
                // large and faint in the top end corner. No glow, no sheen.
                drawRect(
                    Brush.linearGradient(
                        listOf(lerp(fill, Color.White, 0.10f), fill, lerp(fill, HalalaColors.Bg, 0.22f)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                )
                val corner = Offset(if (layoutDirection == LayoutDirection.Rtl) 0f else size.width, 0f)
                drawCircle(HalalaColors.BalancePill, radius = size.height * 0.95f, center = corner, style = Stroke(RING.toPx()))
                drawCircle(HalalaColors.BalancePill, radius = size.height * 0.72f, center = corner, style = Stroke(RING.toPx() / 2))
            }
            .padding(Insets.balanceCard),
        verticalArrangement = Arrangement.spacedBy(Insets.balanceCardGap)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = overline.uppercase(), style = HalalaType.Overline, color = HalalaColors.OnAccent)

            AnimatedContent(
                targetState = status,
                transitionSpec = { (fadeIn(tween(FADE_MS)) + scaleIn(initialScale = 0.9f)) togetherWith fadeOut(tween(FADE_MS)) },
                label = "status"
            ) {
                Text(
                    text = it,
                    style = HalalaType.Caption.copy(fontWeight = FontWeight(600)),
                    color = HalalaColors.OnAccent,
                    modifier = Modifier
                        .clip(Radius.pill)
                        .background(HalalaColors.BalancePill)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
                )
            }
        }

        RollingAmount(
            text = spent,
            style = HalalaNumbers.AmountHero,
            color = HalalaColors.OnAccent,
            currency = currency,
            currencyStyle = HalalaNumbers.AmountHero.copy(fontSize = CURRENCY_SIZE)
        )

        Box(
            Modifier
                .fillMaxWidth()
                .height(Sizes.balanceTrack)
                .clip(Radius.bar)
                .background(HalalaColors.BalanceTrack)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(shown.value)
                    .fillMaxHeight()
                    .clip(Radius.bar)
                    .background(HalalaColors.OnAccent)
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = footStart, style = FOOT, color = HalalaColors.OnAccent)
            Text(text = footEnd, style = FOOT, color = HalalaColors.OnAccent)
        }
    }
}

private const val FADE_MS = 400
/** The width of the coin's outer ring. */
private val RING = Spacing.xs

/** The currency after the hero figure, set small as on the board. */
private val CURRENCY_SIZE = 16.sp
private val FOOT = HalalaType.Label.copy(fontWeight = FontWeight(500))

@Preview
@Composable
private fun BalanceCardPreview() = HalalaTheme {
    Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.card)) {
        BalanceCard("Spent this pay cycle", "On track", "6,240", "SAR", 0.69f, BudgetState.OK, "of 9,000 · 12 days left", "End ≈ 4,180")
        BalanceCard("Spent this pay cycle", "Spending fast", "7,790", "SAR", 0.87f, BudgetState.WARN, "of 9,000 · 12 days left", "End ≈ 2,050")
        BalanceCard("Spent this pay cycle", "Over budget", "9,720", "SAR", 1f, BudgetState.OVER, "720 over · 12 days left", "End ≈ 610")
    }
}
