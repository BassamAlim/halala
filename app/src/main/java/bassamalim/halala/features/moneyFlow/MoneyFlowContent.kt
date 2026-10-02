package bassamalim.halala.features.moneyFlow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.ui.components.ChipStyle
import bassamalim.halala.core.ui.components.ChoiceSheet
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.components.HalalaCard
import bassamalim.halala.core.ui.components.HalalaChip
import bassamalim.halala.core.ui.components.Sankey
import bassamalim.halala.core.ui.components.SankeyBand
import bassamalim.halala.core.ui.components.appendCurrency
import bassamalim.halala.core.ui.components.currencyInlineContent
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Radius
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/** The Money flow board, under Activity's title: the month and account, the headline, the Sankey and the moves with a side missing. */
@Composable
fun MoneyFlowContent(viewModel: MoneyFlowViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.isLoading) return

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.card)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            HalalaChip(label = state.monthName, style = ChipStyle.Accent, onClick = viewModel::onMonthClick)
            if (state.accountName.isNotEmpty()) HalalaChip(
                label = stringResource(R.string.flow_from, state.accountName),
                style = ChipStyle.Outline,
                onClick = viewModel::onAccountClick
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = state.salaryDay?.let { stringResource(R.string.flow_salary_in, it) } ?: stringResource(R.string.flow_came_in, state.monthName),
                style = HalalaType.Label,
                color = HalalaColors.TextMuted
            )
            Text(
                text = buildAnnotatedString {
                    append(state.headline)
                    appendCurrency(state.currency)
                },
                style = HalalaNumbers.AmountXl,
                inlineContent = currencyInlineContent(HalalaColors.TextMuted)
            )
            Text(
                text = state.moved?.let { stringResource(R.string.flow_moved, it) } ?: stringResource(R.string.flow_none_moved),
                style = HalalaType.Label,
                color = HalalaColors.TextMuted
            )
        }

        if (state.nodes.isNotEmpty()) {
            val spent = stringResource(R.string.flow_spent_from, state.accountName)
            val kept = stringResource(R.string.flow_stays_in, state.accountName)
            val bands = state.nodes.map { node ->
                when (node.kind) {
                    NodeKind.ACCOUNT -> SankeyBand(node.label.orEmpty(), node.amount, node.weight, HalalaColors.Accent)
                    NodeKind.SPENT -> SankeyBand(spent, node.amount, node.weight, HalalaColors.StateOver)
                    NodeKind.KEPT -> SankeyBand(kept, node.amount, node.weight, HalalaColors.TextMuted)
                }
            }
            HalalaCard(Modifier.fillMaxWidth()) {
                Sankey(
                    bands = bands,
                    sourceWeight = state.sourceWeight,
                    sourceColor = HalalaColors.Accent,
                    description = bands.joinToString { "${it.label} ${it.amount}" }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.card)) {
                Legend(HalalaColors.Accent, stringResource(R.string.flow_to_accounts))
                Legend(HalalaColors.StateOver, stringResource(R.string.flow_spent))
            }
        } else {
            Text(text = stringResource(R.string.flow_empty), style = HalalaType.Body, color = HalalaColors.TextMuted)
        }

        if (state.unmatched.isNotEmpty()) {
            val leg = state.unmatched.first()
            HalalaCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Text(
                            text = pluralStringResource(R.plurals.flow_no_match, state.unmatched.size, state.unmatched.size),
                            style = HalalaType.BodyStrong
                        )
                        Text(
                            text = stringResource(if (leg.outgoing) R.string.flow_no_match_out else R.string.flow_no_match_in, leg.amount, leg.account, leg.day),
                            style = HalalaType.Caption,
                            color = HalalaColors.TextMuted
                        )
                    }
                    Text(text = leg.amount, style = HalalaNumbers.Amount, color = HalalaColors.TextMuted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    HalalaButton(
                        text = stringResource(if (leg.outgoing) R.string.flow_went_to_someone else R.string.flow_came_from_someone),
                        onClick = { viewModel.onWentToSomeone(leg.id) },
                        modifier = Modifier.weight(1f)
                    )
                    HalalaButton(
                        text = stringResource(R.string.flow_pick_account),
                        onClick = { viewModel.onPickAccount(leg.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    when (val sheet = state.sheet) {
        FlowSheet.Month -> ChoiceSheet(
            title = stringResource(R.string.flow_month),
            options = state.months.map { it.first },
            selected = state.month,
            label = { month -> state.months.first { it.first == month }.second },
            onPick = viewModel::onMonthPicked,
            onDismiss = viewModel::onSheetDismiss
        )
        FlowSheet.Account -> ChoiceSheet(
            title = stringResource(R.string.flow_account),
            options = state.accounts.map { it.id },
            selected = state.accountId,
            label = { id -> state.accounts.first { it.id == id }.label },
            onPick = viewModel::onAccountPicked,
            onDismiss = viewModel::onSheetDismiss
        )
        is FlowSheet.PickAccount -> ChoiceSheet(
            title = stringResource(R.string.flow_pick_account),
            options = sheet.options.map { it.id },
            selected = null,
            label = { id -> sheet.options.first { it.id == id }.label },
            onPick = { viewModel.onCounterpartPicked(sheet.legId, it) },
            onDismiss = viewModel::onSheetDismiss
        )
        null -> Unit
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(Modifier.size(Sizes.stepDot).clip(Radius.xs).background(color))
        Text(text = label, style = HalalaType.Caption, color = HalalaColors.TextMuted)
    }
}
