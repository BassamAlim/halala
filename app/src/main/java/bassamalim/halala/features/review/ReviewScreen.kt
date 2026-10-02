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

/**
 * The review inbox, from the Review board as far as rules alone can fill it: uncategorised
 * spending a merchant at a time, the most money first, each card filed with one answer that is
 * remembered. Suggestions with their confidence and evidence, the Suggested / Needs you filter,
 * swiping, loans and splits, and the snooze row arrive with AI, people and reminders.
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
                        // One transaction opens; many are a list Activity's search already shows.
                        onClick = if (card.count == 1) ({ viewModel.onTransactionClick(card.transactionId) }) else null
                    )
                    HalalaButton(
                        text = stringResource(R.string.choose_category),
                        onClick = { viewModel.onChooseClick(card) },
                        modifier = Modifier.fillMaxWidth()
                    )
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
            selected = null,
            label = { it.name },
            onPick = viewModel::onCategoryPick,
            onDismiss = viewModel::onPickDismiss
        )
    }
}
