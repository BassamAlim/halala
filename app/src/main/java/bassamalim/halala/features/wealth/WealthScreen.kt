package bassamalim.halala.features.wealth

import bassamalim.halala.core.ui.components.Skeleton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.Globals
import bassamalim.halala.core.domain.WealthClass
import bassamalim.halala.core.ui.components.ChipStyle
import bassamalim.halala.core.ui.components.HalalaCard
import bassamalim.halala.core.ui.components.HalalaChip
import bassamalim.halala.core.ui.components.LineChart
import bassamalim.halala.core.ui.components.ListCard
import bassamalim.halala.core.ui.components.ListRow
import bassamalim.halala.core.ui.components.ProgressBar
import bassamalim.halala.core.ui.components.ScreenTitle
import bassamalim.halala.core.ui.components.appendCurrency
import bassamalim.halala.core.ui.components.currencyInlineContent
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/**
 * The Wealth tab, from the Net worth board: the total, how it moved this month and year, the
 * timeline over 3 months, a year or all of it, and what it is made of. Then the accounts, the
 * assets, the people, and zakat.
 */
@Composable
fun WealthScreen(viewModel: WealthViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.card)
    ) {
        ScreenTitle(stringResource(R.string.net_worth)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                WealthRange.entries.forEach { range ->
                    HalalaChip(
                        label = stringResource(
                            when (range) {
                                WealthRange.THREE_MONTHS -> R.string.range_3m
                                WealthRange.YEAR -> R.string.range_1y
                                WealthRange.ALL -> R.string.range_all
                            }
                        ),
                        style = if (range == state.range) ChipStyle.On else ChipStyle.Outline,
                        onClick = { viewModel.onRangeClick(range) }
                    )
                }
            }
        }
        if (state.isLoading) {
            Skeleton()
            return@Column
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = buildAnnotatedString {
                    append(state.total)
                    appendCurrency(Globals.PRIMARY_CURRENCY)
                },
                style = HalalaNumbers.AmountXl,
                inlineContent = currencyInlineContent(HalalaColors.TextMuted)
            )
            val month = state.monthChange?.let { change ->
                state.monthPercent?.let { stringResource(R.string.net_worth_month_percent, change, it) }
                    ?: stringResource(R.string.net_worth_month, change)
            }
            val year = state.yearChange?.let { stringResource(R.string.net_worth_year, it) }
            listOfNotNull(month, year).takeIf { it.isNotEmpty() }?.let {
                Text(
                    text = it.joinToString(" · "),
                    style = HalalaType.Label,
                    color = if (state.rising) HalalaColors.Income else HalalaColors.TextMuted
                )
            }
        }

        LineChart(
            values = state.chart,
            labels = state.chartLabels,
            description = stringResource(R.string.net_worth_chart),
            height = Sizes.fab * 2 + Sizes.chip
        )

        if (state.parts.isNotEmpty()) HalalaCard(modifier = Modifier.fillMaxWidth()) {
            state.parts.forEachIndexed { index, part ->
                if (index > 0) HorizontalDivider(thickness = Sizes.border, color = HalalaColors.Line)
                Column(Modifier.padding(vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Text(text = stringResource(labelOf(part.kind)), style = HalalaType.Body)
                        Text(text = part.amount, style = HalalaNumbers.Amount)
                    }
                    ProgressBar(progress = part.fraction)
                }
            }
        }

        state.maturing?.let { (months, day, profit) ->
            HalalaCard(modifier = Modifier.fillMaxWidth(), onClick = viewModel::onSavingsClick) {
                Text(text = stringResource(R.string.savings_awaeed_months, pluralStringResource(R.plurals.savings_months, months, months)), style = HalalaType.BodyStrong)
                Text(text = stringResource(R.string.savings_matures, day, profit), style = HalalaType.Caption, color = HalalaColors.TextMuted)
            }
        }

        ListCard(Modifier.fillMaxWidth()) {
            ListRow(
                title = stringResource(R.string.wealth_savings),
                subtitle = stringResource(R.string.savings_summary),
                onClick = viewModel::onSavingsClick
            )
            ListRow(
                title = stringResource(R.string.wealth_accounts),
                divider = true,
                subtitle = pluralStringResource(R.plurals.account_count, state.accountCount, state.accountCount),
                onClick = viewModel::onAccountsClick
            )
            ListRow(
                title = stringResource(R.string.assets),
                subtitle = if (state.assetCount == 0) stringResource(R.string.assets_summary)
                else pluralStringResource(R.plurals.asset_count, state.assetCount, state.assetCount),
                divider = true,
                onClick = viewModel::onAssetsClick
            )
            ListRow(
                title = stringResource(R.string.people),
                subtitle = pluralStringResource(R.plurals.people_count, state.peopleCount, state.peopleCount),
                divider = true,
                onClick = viewModel::onPeopleClick
            )
            ListRow(
                title = stringResource(R.string.zakat),
                subtitle = stringResource(R.string.zakat_summary),
                divider = true,
                onClick = viewModel::onZakatClick
            )
        }
        Text(text = stringResource(R.string.net_worth_hint), style = HalalaType.Caption, color = HalalaColors.TextMuted)
    }
}

private fun labelOf(kind: WealthClass) = when (kind) {
    WealthClass.ACCOUNTS -> R.string.wealth_accounts
    WealthClass.SAVINGS -> R.string.wealth_savings
    WealthClass.FUNDS -> R.string.wealth_funds
    WealthClass.GOLD -> R.string.wealth_gold
    WealthClass.OTHER_ASSETS -> R.string.wealth_other
    WealthClass.OWED_TO_YOU -> R.string.people_owed_to_you
    WealthClass.YOU_OWE -> R.string.people_you_owe
}
