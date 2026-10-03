package bassamalim.halala.features.insights

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.Globals
import bassamalim.halala.core.ui.components.Donut
import bassamalim.halala.core.ui.components.HalalaCard
import bassamalim.halala.core.ui.components.LineChart
import bassamalim.halala.core.ui.components.ProgressBar
import bassamalim.halala.core.ui.components.appendCurrency
import bassamalim.halala.core.ui.components.currencyInlineContent
import bassamalim.halala.core.ui.settle
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Radius
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing
import kotlin.math.abs

/**
 * Spending is never coloured, so the category ring is one ink in steps, the biggest boldest;
 * the jade goes to the month picked on the bars.
 */
private val SLICE_INKS = listOf(1f, 0.72f, 0.52f, 0.38f, 0.27f, 0.18f).map { HalalaColors.Text.copy(alpha = it) }

/**
 * Activity's Insights (no board): what a month cost against the five before (tap a bar to look
 * at that month), where it went by category, how it built up against the month before, and
 * where the most went.
 */
@Composable
fun InsightsContent(viewModel: InsightsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.isLoading) return

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.card)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = stringResource(R.string.insights_spent_in, state.monthName), style = HalalaType.Label, color = HalalaColors.TextMuted)
            Text(
                text = buildAnnotatedString {
                    append(state.spent)
                    appendCurrency(Globals.PRIMARY_CURRENCY)
                },
                style = HalalaNumbers.AmountXl,
                inlineContent = currencyInlineContent(HalalaColors.TextMuted)
            )
            state.change?.let {
                Text(
                    text = when {
                        it > 0 -> stringResource(R.string.insights_more_than, it, state.previousMonthName)
                        it < 0 -> stringResource(R.string.insights_less_than, abs(it), state.previousMonthName)
                        else -> stringResource(R.string.insights_same_as, state.previousMonthName)
                    },
                    style = HalalaType.Label,
                    color = HalalaColors.TextMuted
                )
            }
        }

        HalalaCard(label = stringResource(R.string.insights_by_month), modifier = Modifier.fillMaxWidth()) {
            MonthBars(state.months, viewModel::onMonthClick)
        }

        HalalaCard(label = stringResource(R.string.insights_by_category), modifier = Modifier.fillMaxWidth()) {
            if (state.slices.isEmpty()) Empty()
            else {
                Donut(
                    fractions = state.slices.map { it.fraction },
                    colors = SLICE_INKS,
                    description = stringResource(R.string.insights_by_category),
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = Spacing.sm)
                ) {
                    Text(text = state.spent, style = HalalaNumbers.Amount)
                    Text(text = state.monthName, style = HalalaType.Caption, color = HalalaColors.TextMuted)
                }
                state.slices.forEachIndexed { i, slice ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(Sizes.legendDot).background(SLICE_INKS[i % SLICE_INKS.size], Radius.pill))
                        Text(
                            text = when (slice.name) {
                                null -> stringResource(R.string.digest_unfiled)
                                "" -> stringResource(R.string.insights_other)
                                else -> slice.name
                            },
                            style = HalalaType.Label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(text = stringResource(R.string.insights_percent, slice.percent), style = HalalaNumbers.Meta, color = HalalaColors.TextMuted)
                        Text(text = slice.amount, style = HalalaNumbers.Meta)
                    }
                }
            }
        }

        if (state.pace.size >= 2) HalalaCard(label = stringResource(R.string.insights_pace), modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.insights_pace_caption, state.previousMonthName),
                style = HalalaType.Caption,
                color = HalalaColors.TextMuted
            )
            LineChart(
                values = state.pace,
                secondary = state.pacePrevious,
                labels = state.paceLabels,
                tips = state.paceTips,
                description = stringResource(R.string.insights_pace),
                height = Sizes.barChart,
                modifier = Modifier.padding(top = Spacing.sm)
            )
        }

        HalalaCard(label = stringResource(R.string.insights_top_merchants), modifier = Modifier.fillMaxWidth()) {
            if (state.merchants.isEmpty()) Empty()
            state.merchants.forEach { bar ->
                Column(
                    modifier = Modifier
                        .clickable(role = Role.Button) { viewModel.onMerchantClick(bar.id) }
                        .padding(vertical = Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(text = bar.name, style = HalalaType.Label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(text = bar.amount, style = HalalaNumbers.Meta)
                    }
                    ProgressBar(progress = bar.fraction)
                }
            }
        }
    }
}

@Composable
private fun Empty() = Text(text = stringResource(R.string.insights_nothing), style = HalalaType.Body, color = HalalaColors.TextMuted)

/** Six months side by side, the picked one jade; a bar fills from nothing and glides when it moves. */
@Composable
private fun MonthBars(months: List<MonthBar>, onClick: (java.time.YearMonth) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        months.forEach { bar ->
            val height by animateFloatAsState(bar.fraction, settle(), label = "month bar")
            val fill by animateColorAsState(if (bar.selected) HalalaColors.Accent else HalalaColors.Surface2, label = "month fill")
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(role = Role.Tab) { onClick(bar.month) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Box(Modifier.fillMaxWidth().height(Sizes.barChart), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(height.coerceAtLeast(MIN_BAR))
                            .background(fill, RoundedCornerShape(topStart = Radius.smSize / 2, topEnd = Radius.smSize / 2))
                    )
                }
                Text(
                    text = bar.label,
                    style = HalalaType.Caption,
                    color = if (bar.selected) HalalaColors.Text else HalalaColors.TextMuted
                )
            }
        }
    }
}

/** An empty month still shows a sliver, so there is something to tap. */
private const val MIN_BAR = 0.02f
