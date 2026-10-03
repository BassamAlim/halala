package bassamalim.halala.features.forecast

import bassamalim.halala.core.ui.components.Skeleton
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.Globals
import bassamalim.halala.core.ui.components.DateDialog
import bassamalim.halala.core.ui.components.FormField
import bassamalim.halala.core.ui.components.HalalaCard
import bassamalim.halala.core.ui.components.HalalaChip
import bassamalim.halala.core.ui.components.HalalaTextField
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.components.appendCurrency
import bassamalim.halala.core.ui.components.currencyInlineContent
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Radius
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/**
 * The Forecast board: where this cycle should end and its likely range, the balance through the
 * cycle (solid so far, dashed ahead, the band at its end), what each coming month leaves over,
 * and "Can I afford it?".
 */
@Composable
fun ForecastScreen(viewModel: ForecastViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.section),
        verticalArrangement = Arrangement.spacedBy(Spacing.card)
    ) {
        TopBar(title = stringResource(R.string.forecast), onBack = viewModel::onBackClick)
        if (state.isLoading) {
            Skeleton()
            return@Column
        }

        val end = state.end
        if (end == null) {
            Text(text = stringResource(R.string.forecast_not_yet), style = HalalaType.Body, color = HalalaColors.TextMuted)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(text = stringResource(R.string.forecast_end_of_cycle), style = HalalaType.Label, color = HalalaColors.TextMuted)
                Text(
                    text = buildAnnotatedString {
                        append("≈ ")
                        append(end)
                        appendCurrency(Globals.PRIMARY_CURRENCY)
                    },
                    style = HalalaNumbers.AmountXl,
                    inlineContent = currencyInlineContent(HalalaColors.TextMuted)
                )
                val range = stringResource(R.string.forecast_range, state.low, state.high)
                val salary = if (state.salaryFrom != null && state.salaryTo != null)
                    " " + stringResource(R.string.forecast_salary, state.salaryFrom!!, state.salaryTo!!) else ""
                Text(text = range + salary, style = HalalaType.Label, color = HalalaColors.TextMuted)
            }
            state.chart?.let { BalanceChart(it, stringResource(R.string.forecast_today)) }
        }

        if (state.months.isNotEmpty()) HalalaCard(label = stringResource(R.string.forecast_left_over), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                state.months.forEach { bar ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Box(Modifier.height(BAR_AREA).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            Box(
                                Modifier
                                    .width(BAR_WIDTH)
                                    .fillMaxHeight(bar.fraction.coerceAtLeast(MIN_BAR))
                                    .background(if (bar.negative) HalalaColors.StateOver else HalalaColors.Accent, Radius.xs)
                            )
                        }
                        Text(
                            text = bar.left,
                            style = HalalaNumbers.Meta,
                            color = if (bar.negative) HalalaColors.StateOver else HalalaColors.Text,
                            maxLines = 1
                        )
                        Text(text = bar.label, style = HalalaType.Caption, color = HalalaColors.TextMuted)
                    }
                }
            }
            state.dip?.let { dip ->
                Text(
                    text = stringResource(
                        R.string.forecast_dip,
                        dip.month,
                        dip.items.joinToString(stringResource(R.string.forecast_and)) { (name, amount) -> "$name $amount" }
                    ),
                    style = HalalaType.Caption,
                    color = HalalaColors.TextMuted
                )
            }
        }

        AffordCard(state.afford, viewModel)
    }

    if (state.afford.picking) DateDialog(
        date = state.afford.pickFrom,
        onPicked = viewModel::onDatePicked,
        onDismiss = viewModel::onDateDismiss
    )
}

@Composable
private fun AffordCard(form: AffordForm, viewModel: ForecastViewModel) {
    HalalaCard(label = stringResource(R.string.forecast_afford), modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.Bottom) {
            FormField(label = stringResource(R.string.transaction_amount), modifier = Modifier.weight(1f)) {
                HalalaTextField(value = form.amount, onValueChange = viewModel::onAmountChange, numeric = true)
            }
            FormField(label = stringResource(R.string.forecast_when), modifier = Modifier.weight(1f)) {
                HalalaChip(
                    label = form.onLabel.ifEmpty { stringResource(R.string.choose) },
                    onClick = viewModel::onDateClick
                )
            }
        }
        form.result?.let { result ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HalalaColors.Surface2, Radius.md)
                    .padding(Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Icon(
                    painter = painterResource(if (result.affordable) R.drawable.ic_bolt else R.drawable.ic_warning),
                    contentDescription = null,
                    tint = if (result.affordable) HalalaColors.Accent else HalalaColors.StateOver,
                    modifier = Modifier.size(Sizes.iconSmall)
                )
                val answer = stringResource(
                    if (result.affordable) R.string.forecast_afford_yes else R.string.forecast_afford_no,
                    result.lowest, result.on
                )
                val budget = if (result.overBudget) " " + stringResource(R.string.forecast_afford_budget) else ""
                Text(text = answer + budget, style = HalalaType.Label)
            }
        }
    }
}

/** The cycle's balance: solid to today, dashed to its end, with the likely range shaded at the end. */
@Composable
private fun BalanceChart(chart: ForecastChart, todayLabel: String) {
    val values = chart.past + chart.future + listOf(chart.endLow, chart.endHigh)
    val min = minOf(values.min(), 0L)
    val max = values.max().coerceAtLeast(min + 1)
    val total = (chart.past.size + chart.future.size - 2).coerceAtLeast(1)
    val line = HalalaColors.Accent
    val grid = HalalaColors.Line

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT)
                .semantics { contentDescription = todayLabel }
        ) {
            fun x(i: Int) = size.width * i / total
            fun y(v: Long) = size.height - size.height * ((v - min).toFloat() / (max - min).toFloat())
            val todayIndex = chart.past.size - 1

            val hair = Sizes.border.toPx()
            val stroke = hair * 2
            val dash = Spacing.sm.toPx()
            drawLine(grid, Offset(0f, y(0)), Offset(size.width, y(0)), strokeWidth = hair)
            drawLine(grid, Offset(x(todayIndex), 0f), Offset(x(todayIndex), size.height), strokeWidth = hair,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash / 2, dash / 2)))

            val band = Path().apply {
                moveTo(x(todayIndex), y(chart.future.first()))
                lineTo(size.width, y(chart.endHigh))
                lineTo(size.width, y(chart.endLow))
                close()
            }
            drawPath(band, line.copy(alpha = 0.12f))

            val past = Path().apply {
                chart.past.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
            }
            drawPath(past, line, style = Stroke(width = stroke))
            val future = Path().apply {
                chart.future.forEachIndexed { i, v ->
                    val at = todayIndex + i
                    if (i == 0) moveTo(x(at), y(v)) else lineTo(x(at), y(v))
                }
            }
            drawPath(future, line, style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))))
            drawCircle(line, radius = Spacing.xs.toPx(), center = Offset(x(todayIndex), y(chart.past.last())))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = chart.startLabel, style = HalalaType.Caption, color = HalalaColors.TextMuted)
            Text(text = todayLabel, style = HalalaType.Caption, color = HalalaColors.TextMuted)
            Text(text = chart.endLabel, style = HalalaType.Caption, color = HalalaColors.TextMuted)
        }
    }
}

private val CHART_HEIGHT = Sizes.fab * 2 + Sizes.chip
private val BAR_AREA = Sizes.fab + Spacing.md
private val BAR_WIDTH = Sizes.chip - Spacing.sm
private const val MIN_BAR = 0.04f
