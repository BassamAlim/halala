package bassamalim.halala.features.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
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
import bassamalim.halala.core.ui.auditSentence
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.dayText
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/**
 * What you changed about how transactions are filed, newest first, each with its undo. No board
 * draws this screen, so it is the Rules board's rows with a button where the chevron was.
 */
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop)
    ) {
        TopBar(title = stringResource(R.string.history), onBack = viewModel::onBackClick)

        if (state.isLoading) return@Column

        if (state.changes.isEmpty()) {
            Text(
                text = stringResource(R.string.history_empty),
                style = HalalaType.Body,
                color = HalalaColors.TextMuted,
                modifier = Modifier.padding(top = Spacing.card)
            )
        }

        LazyColumn(contentPadding = PaddingValues(top = Spacing.sm, bottom = Spacing.section)) {
            itemsIndexed(state.changes, key = { _, change -> change.id }) { index, change ->
                if (index > 0) HorizontalDivider(thickness = Sizes.border, color = HalalaColors.Line)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Sizes.touchTarget)
                        .padding(vertical = Insets.row),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Text(
                            text = auditSentence(change.action, change.subject, change.detail),
                            style = HalalaType.Body
                        )
                        Text(
                            text = stringResource(
                                R.string.meta_pair,
                                pluralStringResource(R.plurals.transaction_count, change.transactions, change.transactions),
                                dayText(change.day)
                            ),
                            style = HalalaType.Caption,
                            color = HalalaColors.TextMuted
                        )
                    }

                    if (change.undone) {
                        Text(
                            text = stringResource(R.string.undone),
                            style = HalalaType.Label,
                            color = HalalaColors.TextMuted
                        )
                    } else {
                        HalalaButton(
                            text = stringResource(R.string.undo),
                            onClick = { viewModel.onUndoClick(change.id) }
                        )
                    }
                }
            }
        }
    }
}
