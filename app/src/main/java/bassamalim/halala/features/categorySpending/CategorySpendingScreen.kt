package bassamalim.halala.features.categorySpending

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.ui.components.GroupLabel
import bassamalim.halala.core.ui.components.HalalaCard
import bassamalim.halala.core.ui.components.ProgressBar
import bassamalim.halala.core.ui.components.Skeleton
import bassamalim.halala.core.ui.components.SummaryCard
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.components.TransactionItemRow
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Spacing

/**
 * What a category came to in one month, from Insights' category list (no board): the total,
 * where it went by merchant (as Insights' top merchants), and each transaction.
 */
@Composable
fun CategorySpendingScreen(viewModel: CategorySpendingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop)
    ) {
        TopBar(
            title = state.name ?: stringResource(R.string.digest_unfiled),
            onBack = viewModel::onBackClick
        )

        if (state.isLoading) {
            Skeleton()
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.section)
        ) {
            item {
                SummaryCard(
                    label = stringResource(R.string.insights_spent_in, state.monthName),
                    amount = state.total,
                    currency = state.currency,
                    caption = pluralStringResource(R.plurals.transaction_count, state.count, state.count),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.card)
                )
            }

            if (state.merchants.isNotEmpty()) item {
                HalalaCard(
                    label = stringResource(R.string.insights_by_merchant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.card)
                ) {
                    state.merchants.forEach { bar ->
                        Column(
                            modifier = Modifier
                                .clickable(role = Role.Button) { viewModel.onMerchantClick(bar.id) }
                                .padding(vertical = Spacing.xs),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                Text(
                                    text = bar.name,
                                    style = HalalaType.Label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(text = bar.amount, style = HalalaNumbers.Meta)
                            }
                            ProgressBar(progress = bar.fraction)
                        }
                    }
                }
            }

            if (state.transactions.isEmpty()) item {
                Text(text = stringResource(R.string.insights_nothing), style = HalalaType.Body, color = HalalaColors.TextMuted)
            }
            else item { GroupLabel(stringResource(R.string.merchant_transactions)) }

            itemsIndexed(state.transactions, key = { _, item -> item.id }) { index, item ->
                TransactionItemRow(
                    item = item,
                    onClick = { viewModel.onTransactionClick(item.id) },
                    divider = index > 0,
                    withDay = true
                )
            }
        }
    }
}
