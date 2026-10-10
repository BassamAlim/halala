package bassamalim.halala.features.moneyFlow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import bassamalim.halala.core.ui.components.GroupLabel
import bassamalim.halala.core.ui.components.ListCard
import bassamalim.halala.core.ui.components.ListRow
import bassamalim.halala.core.domain.PartKind
import kotlin.math.abs
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

/**
 * The Money flow board, under the Insights tab's title: the month and account, the headline and how
 * much was spent and kept, the Sankey (top to bottom: where it came from, the account, where it
 * went; tap a part to see what makes it up), the parts named in two lists, and the moves with a
 * side missing.
 */
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

        if (state.uses.isNotEmpty() || state.sources.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = stringResource(R.string.flow_shares, state.spentPercent, state.keptPercent),
                    style = HalalaType.Body
                )
                state.spentChange?.takeIf { it != 0 }?.let { change ->
                    Text(
                        text = stringResource(if (change > 0) R.string.flow_spending_up else R.string.flow_spending_down, abs(change)),
                        style = HalalaType.Label,
                        color = HalalaColors.TextMuted
                    )
                }
            }

            val sources = state.sources.map { SankeyBand(it.key, it.weight, colorOf(it.kind)) }
            val uses = state.uses.map { SankeyBand(it.key, it.weight, colorOf(it.kind)) }
            val came = stringResource(R.string.flow_came)
            val went = stringResource(R.string.flow_went)
            val labels = (state.sources + state.uses).associate { it.key to labelOf(it, state.accountName) }
            HalalaCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(text = came, style = HalalaType.Caption, color = HalalaColors.TextMuted)
                Sankey(
                    sources = sources,
                    uses = uses,
                    middleColor = HalalaColors.TextMuted,
                    selected = state.selected,
                    onSelect = viewModel::onPartClick,
                    description = "$came: " + state.sources.joinToString { "${labels[it.key]} ${it.amount}" } +
                            ". $went: " + state.uses.joinToString { "${labels[it.key]} ${it.amount}" }
                )
                Text(text = went, style = HalalaType.Caption, color = HalalaColors.TextMuted)
                Text(
                    text = stringResource(R.string.flow_tap_hint),
                    style = HalalaType.Label,
                    color = HalalaColors.TextMuted
                )
            }

            PartList(came, state.sources, labels, state.selected, viewModel)
            PartList(went, state.uses, labels, state.selected, viewModel)
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

/** One end's parts, named, with what each came to; the chosen one opens on what makes it up. */
@Composable
private fun PartList(title: String, parts: List<FlowPartUi>, labels: Map<String, String>, selected: String?, viewModel: MoneyFlowViewModel) {
    if (parts.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        GroupLabel(title)
        ListCard(Modifier.fillMaxWidth()) {
            parts.forEachIndexed { index, part ->
                val share = stringResource(R.string.flow_share, part.percent)
                val change = part.change?.takeIf { it != 0 }?.let {
                    stringResource(if (it > 0) R.string.flow_change_up else R.string.flow_change_down, abs(it))
                }
                ListRow(
                    title = labels[part.key].orEmpty(),
                    subtitle = change?.let { stringResource(R.string.meta_pair, share, it) } ?: share,
                    divider = index > 0,
                    leading = {
                        Box(
                            Modifier
                                .size(Sizes.stepDot)
                                .clip(Radius.xs)
                                .background(colorOf(part.kind).copy(alpha = if (selected == null || selected == part.key) 1f else DIMMED))
                        )
                    },
                    trailing = {
                        Text(
                            text = part.amount,
                            style = HalalaNumbers.Amount,
                            color = if (part.kind.incoming && part.kind != PartKind.FROM_ACCOUNT && part.kind != PartKind.FROM_BALANCE) HalalaColors.Income
                            else if (part.kind.spending) HalalaColors.Text else HalalaColors.TextMuted,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    },
                    onClick = { viewModel.onPartClick(part.key) }
                )
                AnimatedVisibility(visible = selected == part.key) {
                    Column(Modifier.padding(start = Spacing.md, bottom = Spacing.sm)) {
                        part.items.forEach { item ->
                            ListRow(
                                title = item.title.ifBlank { labels[part.key].orEmpty() },
                                subtitle = item.day,
                                trailing = {
                                    Text(
                                        text = item.amount,
                                        style = HalalaNumbers.Meta,
                                        color = HalalaColors.TextMuted,
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    )
                                },
                                onClick = { viewModel.onItemClick(item.id) }
                            )
                        }
                        if (part.more > 0) Text(
                            text = pluralStringResource(R.plurals.flow_more, part.more, part.more),
                            style = HalalaType.Label,
                            color = HalalaColors.TextMuted
                        )
                    }
                }
            }
        }
    }
}

/**
 * Colour is meaning: money in is jade, money that stayed yours (moved, saved, kept) is quiet
 * blue or muted, and spending is plain, never coloured.
 */
private fun colorOf(kind: PartKind): Color = when (kind) {
    PartKind.SALARY, PartKind.FROM_PEOPLE, PartKind.REFUNDS, PartKind.OTHER_IN -> HalalaColors.Income
    PartKind.FROM_ACCOUNT, PartKind.TO_ACCOUNT, PartKind.SAVED -> HalalaColors.Info
    PartKind.FROM_BALANCE, PartKind.KEPT -> HalalaColors.TextMuted
    PartKind.TO_PEOPLE, PartKind.CATEGORY, PartKind.OTHER_SPENDING, PartKind.UNFILED, PartKind.OTHER_OUT -> HalalaColors.Text
}

/** A part in words: an account or category by its name, the rest by what they are. */
@Composable
private fun labelOf(part: FlowPartUi, account: String): String = when (part.kind) {
    PartKind.SALARY -> stringResource(R.string.flow_part_salary)
    PartKind.FROM_ACCOUNT -> stringResource(R.string.flow_from, part.name.orEmpty())
    PartKind.FROM_PEOPLE -> stringResource(R.string.flow_part_from_people)
    PartKind.REFUNDS -> stringResource(R.string.flow_part_refunds)
    PartKind.OTHER_IN -> stringResource(R.string.flow_part_other_in)
    PartKind.FROM_BALANCE -> stringResource(R.string.flow_part_from_balance)
    PartKind.TO_ACCOUNT -> stringResource(R.string.flow_part_to, part.name.orEmpty())
    PartKind.SAVED -> stringResource(R.string.flow_part_saved)
    PartKind.TO_PEOPLE -> stringResource(R.string.flow_part_to_people)
    PartKind.CATEGORY -> part.name.orEmpty()
    PartKind.OTHER_SPENDING -> stringResource(R.string.flow_part_other_spending)
    PartKind.UNFILED -> stringResource(R.string.flow_part_unfiled)
    PartKind.OTHER_OUT -> stringResource(R.string.flow_part_other_out)
    PartKind.KEPT -> stringResource(R.string.flow_stays_in, account)
}

/** A part's swatch while another is chosen. */
private const val DIMMED = 0.3f
