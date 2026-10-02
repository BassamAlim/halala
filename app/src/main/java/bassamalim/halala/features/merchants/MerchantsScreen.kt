package bassamalim.halala.features.merchants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.ui.components.ListCard
import bassamalim.halala.core.ui.components.ListRow
import bassamalim.halala.core.ui.components.SearchField
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Spacing

/**
 * Every merchant, the busiest first, to find one and open it. No board draws this screen, so it
 * is Settings' list card under the Rules board's search.
 */
@Composable
fun MerchantsScreen(viewModel: MerchantsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop),
        verticalArrangement = Arrangement.spacedBy(Spacing.card)
    ) {
        TopBar(title = stringResource(R.string.merchants), onBack = viewModel::onBackClick)

        if (state.isLoading) return@Column

        if (!state.hasAny) {
            Text(
                text = stringResource(R.string.merchants_empty),
                style = HalalaType.Body,
                color = HalalaColors.TextMuted
            )
            return@Column
        }

        SearchField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = stringResource(R.string.merchants_search),
            modifier = Modifier.fillMaxWidth()
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = Spacing.section)
        ) {
            if (state.merchants.isNotEmpty()) item {
                ListCard(Modifier.fillMaxWidth()) {
                    state.merchants.forEachIndexed { index, merchant ->
                        ListRow(
                            title = merchant.name,
                            subtitle = stringResource(
                                R.string.meta_pair,
                                pluralStringResource(R.plurals.transaction_count, merchant.transactions, merchant.transactions),
                                pluralStringResource(R.plurals.merchant_aliases, merchant.spellings, merchant.spellings)
                            ),
                            divider = index > 0,
                            onClick = { viewModel.onMerchantClick(merchant.id) }
                        )
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.merchants_hint),
                    style = HalalaType.Caption,
                    color = HalalaColors.TextMuted,
                    modifier = Modifier.padding(top = Spacing.card)
                )
            }
        }
    }
}
