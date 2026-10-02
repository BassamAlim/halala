package bassamalim.halala.features.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import bassamalim.halala.core.ui.components.SegmentedControl
import bassamalim.halala.features.moneyFlow.MoneyFlowContent
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import bassamalim.halala.core.Globals
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
 * out, and the rows by day; its second segment is Money flow.
 */
@Composable
fun ActivityScreen(viewModel: ActivityViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var segment by rememberSaveable { mutableIntStateOf(0) }
    val title = @Composable {
        ScreenTitle(stringResource(R.string.tab_activity)) {
            SegmentedControl(
                options = listOf(stringResource(R.string.activity_transactions), stringResource(R.string.activity_money_flow)),
                selectedIndex = segment,
                onSelect = { segment = it }
            )
        }
    }

    if (segment == 1) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .padding(top = Insets.screenTop, bottom = Sizes.fab + Spacing.section),
            verticalArrangement = Arrangement.spacedBy(Spacing.card)
        ) {
            title()
            MoneyFlowContent()
        }
        return
    }

    ActivityContent(
        title = title,
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onAccountFilterClick = viewModel::onAccountFilterClick,
        onTransactionClick = viewModel::onTransactionClick
    )
}

@Composable
private fun ActivityContent(
    title: @Composable () -> Unit,
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
        item { title() }

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
                modifier = Modifier
                    .height(IntrinsicSize.Min)
                    .padding(top = Spacing.xs, bottom = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Insets.grid)
            ) {
                SummaryCard(
                    label = stringResource(R.string.activity_in),
                    amount = state.monthIn,
                    currency = Globals.PRIMARY_CURRENCY,
                    amountColor = HalalaColors.Income,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                SummaryCard(
                    label = stringResource(R.string.activity_out),
                    amount = state.monthOut,
                    currency = Globals.PRIMARY_CURRENCY,
                    modifier = Modifier.weight(1f).fillMaxHeight()
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
