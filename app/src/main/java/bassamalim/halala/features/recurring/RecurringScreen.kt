package bassamalim.halala.features.recurring

import bassamalim.halala.core.ui.components.Skeleton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.enums.AmountTone
import bassamalim.halala.core.enums.CadenceUnit
import bassamalim.halala.core.enums.RecurringKind
import bassamalim.halala.core.ui.cadenceLabel
import bassamalim.halala.core.ui.components.GroupLabel
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.components.HalalaCard
import bassamalim.halala.core.ui.components.MONEY_MARK
import bassamalim.halala.core.ui.components.MoneyText
import bassamalim.halala.core.ui.components.SummaryCard
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.components.TransactionRow
import bassamalim.halala.core.ui.perLabel
import bassamalim.halala.core.ui.recurringKindLabel
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/**
 * The Subscriptions and bills board: what they cost a month and a year, what needs you (a price
 * rise, a charge that didn't come, one Halala found), then what is due in the next 30 days and
 * later. Tapping one opens it to change.
 */
@Composable
fun RecurringScreen(viewModel: RecurringViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop),
        verticalArrangement = Arrangement.spacedBy(Spacing.card)
    ) {
        TopBar(
            title = stringResource(R.string.recurring_title),
            onBack = viewModel::onBackClick,
            actionLabel = stringResource(R.string.recurring_add),
            onAction = viewModel::onAddClick
        )

        if (state.isLoading) {
            Skeleton()
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = Spacing.section),
            verticalArrangement = Arrangement.spacedBy(Spacing.card)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Insets.grid)) {
                    SummaryCard(stringResource(R.string.recurring_monthly), state.monthly, Modifier.weight(1f), currency = state.currency)
                    SummaryCard(stringResource(R.string.recurring_yearly), state.yearly, Modifier.weight(1f), currency = state.currency)
                }
            }

            for (alert in state.alerts) item(key = "alert-${alert.javaClass.simpleName}-${alert.seriesId}") {
                AlertCard(alert, viewModel)
            }

            if (state.soon.isEmpty() && state.later.isEmpty()) item {
                Text(
                    text = stringResource(R.string.recurring_empty),
                    style = HalalaType.Body,
                    color = HalalaColors.TextMuted
                )
            }
            group(R.string.recurring_soon, state.soon, viewModel)
            group(R.string.recurring_later, state.later, viewModel)
        }
    }
}

private fun LazyListScope.group(label: Int, rows: List<SeriesRow>, viewModel: RecurringViewModel) {
    if (rows.isEmpty()) return
    item(key = "label-$label") {
        Column {
            GroupLabel(stringResource(label))
            rows.forEachIndexed { index, row ->
                TransactionRow(
                    title = row.name,
                    meta = metaOf(row),
                    amount = row.amount,
                    tone = AmountTone.Spending,
                    initial = row.initial,
                    autoLabel = when {
                        row.priceUp -> stringResource(R.string.recurring_badge_price_up)
                        row.kind == RecurringKind.PLANNED -> stringResource(R.string.recurring_badge_planned)
                        row.autoRenew -> stringResource(R.string.recurring_badge_auto_renew)
                        else -> null
                    },
                    badgeColor = if (row.priceUp) HalalaColors.StateWarn else HalalaColors.TextMuted,
                    divider = index > 0,
                    onClick = { viewModel.onSeriesClick(row.id) }
                )
            }
        }
    }
}

/** "Subscription · renews 3 Oct", "Bill · 1 Nov · contract ends Aug 2027", "Yearly · 14 Jan · reminder 30 days before". */
@Composable
private fun metaOf(row: SeriesRow): String {
    val what = if (row.every == 1 && row.unit == CadenceUnit.MONTH) recurringKindLabel(row.kind) else cadenceLabel(row.every, row.unit)
    val due = if (row.autoRenew && row.kind == RecurringKind.SUBSCRIPTION) stringResource(R.string.recurring_renews, row.due) else row.due
    return listOfNotNull(
        what,
        due,
        row.endsOn?.let { stringResource(R.string.recurring_ends, it) },
        row.reminderDays?.let { pluralStringResource(R.plurals.recurring_reminder, it, it) }
    ).joinToString(" · ")
}

@Composable
private fun AlertCard(alert: RecurringAlert, viewModel: RecurringViewModel) {
    HalalaCard(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Icon(
                painter = painterResource(R.drawable.ic_warning),
                contentDescription = null,
                tint = if (alert is RecurringAlert.Proposed) HalalaColors.Info else HalalaColors.StateWarn,
                modifier = Modifier.size(Sizes.iconSmall)
            )
            when (alert) {
                is RecurringAlert.PriceUp -> Text(
                    text = stringResource(R.string.recurring_price_up, alert.name, alert.from, alert.to, perLabel(alert.every, alert.unit)),
                    style = HalalaType.Body
                )
                is RecurringAlert.Missed -> Text(
                    text = stringResource(R.string.recurring_missed, alert.name, alert.expected),
                    style = HalalaType.Body
                )
                is RecurringAlert.Proposed -> MoneyText(
                    text = stringResource(
                        when (alert.kind) {
                            RecurringKind.SUBSCRIPTION -> R.string.recurring_found_subscription
                            RecurringKind.BILL -> R.string.recurring_found_bill
                            RecurringKind.PLANNED -> R.string.recurring_found_planned
                        },
                        alert.name, MONEY_MARK, perLabel(alert.every, alert.unit)
                    ),
                    amount = alert.amount,
                    currency = alert.currency,
                    style = HalalaType.Body,
                    color = HalalaColors.Text
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            when (alert) {
                is RecurringAlert.PriceUp -> {
                    HalalaButton(stringResource(R.string.recurring_keep), { viewModel.onKeepPrice(alert) }, Modifier.weight(1f))
                    HalalaButton(stringResource(R.string.recurring_remind_cancel), { viewModel.onRemindToCancel(alert) }, Modifier.weight(1f))
                }
                is RecurringAlert.Missed ->
                    HalalaButton(stringResource(R.string.recurring_open), { viewModel.onSeriesClick(alert.seriesId) }, Modifier.fillMaxWidth())
                is RecurringAlert.Proposed -> {
                    HalalaButton(stringResource(R.string.recurring_confirm), { viewModel.onConfirm(alert.seriesId) }, Modifier.weight(1f))
                    HalalaButton(stringResource(R.string.recurring_dismiss), { viewModel.onDismiss(alert.seriesId) }, Modifier.weight(1f))
                }
            }
        }
    }
}
