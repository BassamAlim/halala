package bassamalim.halala.core.ui.components

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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.em
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.lg)
            .background(state.color)
            .padding(Insets.balanceCard),
        verticalArrangement = Arrangement.spacedBy(Insets.balanceCardGap)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = overline.uppercase(), style = HalalaType.Overline, color = HalalaColors.OnAccent)

            Text(
                text = status,
                style = HalalaType.Caption.copy(fontWeight = FontWeight(600)),
                color = HalalaColors.OnAccent,
                modifier = Modifier
                    .clip(Radius.pill)
                    .background(HalalaColors.BalancePill)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
            )
        }

        Text(
            text = buildAnnotatedString {
                append(spent)
                withStyle(SpanStyle(fontSize = CURRENCY_SIZE, letterSpacing = 0.em)) { appendCurrency(currency) }
            },
            style = HalalaNumbers.AmountHero,
            color = HalalaColors.OnAccent,
            inlineContent = currencyInlineContent(HalalaColors.OnAccent)
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
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
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
