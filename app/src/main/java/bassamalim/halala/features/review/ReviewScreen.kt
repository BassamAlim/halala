package bassamalim.halala.features.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.enums.AmountTone
import bassamalim.halala.core.enums.AuditAction
import bassamalim.halala.core.ui.components.ChoiceSheet
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.components.HalalaCard
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.components.TransactionRow
import bassamalim.halala.core.ui.auditSentence
import bassamalim.halala.core.ui.dayText
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Spacing
import bassamalim.halala.core.ui.components.HalalaChip
import bassamalim.halala.core.ui.components.ChipStyle
import bassamalim.halala.core.ui.components.ButtonKind
import bassamalim.halala.core.ui.expenseTypeLabel
import bassamalim.halala.core.ui.businessTypeLabel
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle

/**
 * The review inbox, from the Review board: uncategorised spending a merchant at a time, the most
 * money first, each card filed with one answer that is remembered. A merchant that was
 * identified shows what it is (and, from the AI, how sure) with its category chosen, one tap
 * from confirmed; the rest need you. Swiping, loans and splits, and the snooze row arrive with
 * people and reminders.
 */
@Composable
fun ReviewScreen(viewModel: ReviewViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop)
    ) {
        TopBar(title = stringResource(R.string.review), onBack = viewModel::onBackClick)

        if (state.isLoading) return@Column

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.card),
            contentPadding = PaddingValues(top = Spacing.card, bottom = Spacing.section)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = if (state.cards.isEmpty()) stringResource(R.string.review_empty)
                        else pluralStringResource(R.plurals.review_count, state.cards.size, state.cards.size),
                        style = HalalaType.ScreenTitle
                    )
                    if (state.cards.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.review_hint),
                            style = HalalaType.Label,
                            color = HalalaColors.TextMuted
                        )
                    }
                }
            }

            if (state.total > 0) item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ReviewFilter.entries.forEach { filter ->
                        HalalaChip(
                            label = when (filter) {
                                ReviewFilter.All -> stringResource(R.string.review_all, state.total)
                                ReviewFilter.Suggested -> stringResource(R.string.review_suggested, state.suggested)
                                ReviewFilter.NeedsYou -> stringResource(R.string.review_needs_you, state.needsYou)
                            },
                            style = if (filter == state.filter) ChipStyle.Accent else ChipStyle.Outline,
                            onClick = { viewModel.onFilterClick(filter) }
                        )
                    }
                }
            }

            items(state.cards, key = { it.key }) { card ->
                HalalaCard(Modifier.fillMaxWidth()) {
                    TransactionRow(
                        title = card.title,
                        meta = if (card.count > 1)
                            pluralStringResource(R.plurals.review_cluster_meta, card.count, card.count, card.since)
                        else stringResource(R.string.meta_pair, dayText(card.day), card.accountLabel),
                        amount = card.amount,
                        currency = card.currency,
                        tone = AmountTone.Spending,
                        initial = card.initial,
                        // One transaction opens itself; many open their merchant.
                        onClick = if (card.count == 1 || card.merchantId != null) ({ viewModel.onCardClick(card) }) else null
                    )
                    card.businessType?.let { type ->
                        val evidence = stringResource(R.string.identified_as, businessTypeLabel(type))
                        Text(
                            text = card.confidence
                                ?.let { stringResource(R.string.meta_pair, evidence, stringResource(R.string.review_sure, it)) }
                                ?: evidence,
                            style = HalalaType.Label,
                            color = HalalaColors.TextMuted
                        )
                    }

                    val suggestion = card.suggestion
                    if (suggestion == null) {
                        HalalaButton(
                            text = stringResource(R.string.choose_category),
                            onClick = { viewModel.onChooseClick(card) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = buildAnnotatedString {
                                append(suggestion.category.name)
                                suggestion.expenseType?.let { type ->
                                    withStyle(SpanStyle(color = HalalaColors.TextMuted)) {
                                        append(stringResource(R.string.separator))
                                        append(expenseTypeLabel(type))
                                    }
                                }
                            },
                            style = HalalaType.Body
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            HalalaButton(
                                text = pluralStringResource(R.plurals.review_confirm_all, card.count, card.count),
                                onClick = { viewModel.onConfirmClick(card) },
                                kind = ButtonKind.Primary,
                                modifier = Modifier.weight(1f)
                            )
                            HalalaButton(
                                text = stringResource(R.string.review_change),
                                onClick = { viewModel.onChooseClick(card) }
                            )
                        }
                    }
                }
            }
        }

        // The answer just given, one tap from being taken back.
        state.justFiled?.let { filed ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text = auditSentence(AuditAction.LEARNED, filed.merchant, filed.category),
                    style = HalalaType.Label,
                    color = HalalaColors.TextMuted,
                    modifier = Modifier.weight(1f)
                )
                HalalaButton(text = stringResource(R.string.undo), onClick = viewModel::onUndoClick)
            }
        }
    }

    state.picking?.let { card ->
        ChoiceSheet(
            title = card.title,
            options = state.categories,
            selected = card.suggestion?.category,
            label = { it.name },
            onPick = viewModel::onCategoryPick,
            onDismiss = viewModel::onPickDismiss
        )
    }
}
