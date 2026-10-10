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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.Globals
import bassamalim.halala.core.places.LocationAccess
import bassamalim.halala.core.ui.components.CardLabel
import bassamalim.halala.core.ui.components.Donut
import bassamalim.halala.core.ui.components.HalalaCard
import bassamalim.halala.core.ui.components.HeatMap
import bassamalim.halala.core.ui.components.LineChart
import bassamalim.halala.core.ui.components.MapPlaceholder
import bassamalim.halala.core.ui.components.openLocationSettings
import bassamalim.halala.core.ui.components.rememberLocationRequest
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
 * at that month; the chevrons page six months older or newer), where it went by category, how it built up against the month before,
 * where the most went, and where you spent it on the map.
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

        HalalaCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    CardLabel(stringResource(R.string.insights_by_month))
                    Text(text = state.range, style = HalalaType.Caption, color = HalalaColors.TextMuted)
                }
                PageButton(R.string.insights_older, enabled = state.hasOlder, back = true, onClick = viewModel::onOlderClick)
                PageButton(R.string.insights_newer, enabled = state.hasNewer, back = false, onClick = viewModel::onNewerClick)
            }
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
                // Each opens what it was made of; Other opens into its categories, inked as Other.
                state.legend.forEachIndexed { i, slice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Sizes.touchTarget)
                            .clickable(role = Role.Button) { viewModel.onSliceClick(slice) },
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(Sizes.legendDot).background(SLICE_INKS[minOf(i, SLICE_INKS.lastIndex)], Radius.pill))
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
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = HalalaColors.TextMuted,
                            modifier = Modifier
                                .size(Sizes.icon)
                                .then(if (slice.isOther) Modifier.rotate(90f) else Modifier)
                        )
                    }
                }
                if (state.otherOpen) Text(
                    text = stringResource(R.string.insights_fewer),
                    style = HalalaType.Label,
                    color = HalalaColors.TextMuted,
                    modifier = Modifier
                        .heightIn(min = Sizes.touchTarget)
                        .clickable(role = Role.Button, onClick = viewModel::onOtherCloseClick)
                        .wrapContentHeight()
                )
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

        PlacesCard(state, viewModel)
    }
}

/**
 * Where you spend, the picked month: the heat on a small map the page scrolls over, and the
 * places most went; the card opens the whole map. Without location all the time, the
 * street-grid placeholder says why, with the button that fixes it.
 */
@Composable
private fun PlacesCard(state: InsightsUiState, viewModel: InsightsViewModel) {
    val context = LocalContext.current
    val askLocation = rememberLocationRequest(viewModel::onCheckAccess)
    // Back from Android's settings, or the location switch: look again.
    LifecycleResumeEffect(viewModel) {
        viewModel.onCheckAccess()
        onPauseOrDispose { }
    }
    val mapModifier = Modifier
        .fillMaxWidth()
        .height(Sizes.placeMap)
        .clip(Radius.lg)

    HalalaCard(
        label = stringResource(R.string.map_title),
        modifier = Modifier.fillMaxWidth(),
        onClick = viewModel::onMapClick.takeIf { state.access == LocationAccess.GRANTED }
    ) {
        when (state.access) {
            LocationAccess.GRANTED -> {
                Box(mapModifier) {
                    HeatMap(points = state.points, focus = null, modifier = Modifier.matchParentSize())
                    // Takes the touches so the map stays put, the page scrolls and the card opens.
                    Box(Modifier.matchParentSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
                }
                Text(
                    text = if (state.placedCount == 0) stringResource(R.string.map_empty)
                    else pluralStringResource(R.plurals.map_summary, state.placedCount, state.placedCount, state.placedTotal),
                    style = HalalaType.Label,
                    color = HalalaColors.TextMuted
                )
                state.places.forEach { place ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(text = place.name, style = HalalaType.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = pluralStringResource(R.plurals.map_visits, place.count, place.count),
                                style = HalalaType.Caption,
                                color = HalalaColors.TextMuted
                            )
                        }
                        Text(text = place.amount, style = HalalaNumbers.Meta)
                    }
                }
            }
            LocationAccess.SERVICES_OFF -> MapPlaceholder(
                message = stringResource(R.string.map_location_off),
                action = stringResource(R.string.map_turn_location_on),
                onAction = { openLocationSettings(context) },
                modifier = mapModifier
            )
            LocationAccess.FOREGROUND_ONLY -> MapPlaceholder(
                message = stringResource(R.string.map_needs_always),
                action = stringResource(R.string.map_allow_always),
                onAction = askLocation,
                modifier = mapModifier
            )
            LocationAccess.DENIED -> MapPlaceholder(
                message = stringResource(R.string.map_no_permission),
                action = stringResource(R.string.map_allow),
                onAction = askLocation,
                modifier = mapModifier
            )
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

/** A chevron to page the bars six months older ([back]) or newer; dimmed with nowhere to go. */
@Composable
private fun PageButton(description: Int, enabled: Boolean, back: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Sizes.touchTarget)
            .clip(Radius.pill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = stringResource(description),
            tint = if (enabled) HalalaColors.Text else HalalaColors.Line,
            modifier = Modifier
                .size(Sizes.icon)
                .then(if (back) Modifier.rotate(180f) else Modifier)
        )
    }
}

/** An empty month still shows a sliver, so there is something to tap. */
private const val MIN_BAR = 0.02f
