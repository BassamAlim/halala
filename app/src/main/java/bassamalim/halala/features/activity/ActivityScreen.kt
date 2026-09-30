package bassamalim.halala.features.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.ui.components.ChipStyle
import bassamalim.halala.core.ui.components.GroupLabel
import bassamalim.halala.core.ui.components.HalalaChip
import bassamalim.halala.core.ui.components.ScreenTitle
import bassamalim.halala.core.ui.components.SearchField
import bassamalim.halala.core.ui.components.SummaryCard
import bassamalim.halala.core.ui.components.TransactionItemRow
import bassamalim.halala.core.ui.dayText
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/**
 * The transactions feed, from the Activity board: search, account filters, this month's in and
 * out, and the rows by day. Money flow (the board's second segment) arrives in Phase 6.
 */
@Composable
fun ActivityScreen(viewModel: ActivityViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ActivityContent(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onAccountFilterClick = viewModel::onAccountFilterClick,
        onTransactionClick = viewModel::onTransactionClick
    )
}

@Composable
private fun ActivityContent(
    state: ActivityUiState,
    onQueryChange: (String) -> Unit,
    onAccountFilterClick: (Long?) -> Unit,
    onTransactionClick: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.screen,
            end = Spacing.screen,
            top = Insets.screenTop,
            // Clear of the quick-add button.
            bottom = Sizes.fab + Spacing.section
        )
    ) {
        item { ScreenTitle(stringResource(R.string.tab_activity)) }

        item {
            SearchField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = stringResource(R.string.activity_search),
                modifier = Modifier.padding(top = Spacing.card)
            )
        }

        item {
            LazyRow(
                modifier = Modifier.padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                item {
                    HalalaChip(
                        label = stringResource(R.string.all_accounts),
                        style = if (state.selectedAccountId == null) ChipStyle.Accent else ChipStyle.Outline,
                        onClick = { onAccountFilterClick(null) }
                    )
                }
                items(state.accountFilters, key = { it.id }) { filter ->
                    HalalaChip(
                        label = filter.label,
                        style = if (state.selectedAccountId == filter.id) ChipStyle.Accent else ChipStyle.Outline,
                        onClick = { onAccountFilterClick(filter.id) }
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Insets.grid)
            ) {
                SummaryCard(
                    label = stringResource(R.string.activity_in),
                    amount = state.monthIn,
                    amountColor = HalalaColors.Income,
                    modifier = Modifier.weight(1f)
                )
                SummaryCard(
                    label = stringResource(R.string.activity_out),
                    amount = state.monthOut,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (!state.isLoading && state.groups.isEmpty()) {
            item {
                Text(
                    text = stringResource(if (state.hasAny) R.string.activity_no_match else R.string.activity_empty),
                    style = HalalaType.Body,
                    color = HalalaColors.TextMuted,
                    modifier = Modifier.padding(top = Spacing.sm)
                )
            }
        }

        state.groups.forEach { group ->
            item(key = "day-${group.items.first().date}") { GroupLabel(dayText(group.day)) }

            itemsIndexed(group.items, key = { _, item -> item.id }) { index, item ->
                TransactionItemRow(
                    item = item,
                    divider = index > 0,
                    onClick = { onTransactionClick(item.id) }
                )
            }
        }
    }
}
