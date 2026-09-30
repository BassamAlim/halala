package bassamalim.halala.features.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.ui.components.CardLabel
import bassamalim.halala.core.ui.components.SummaryCard
import bassamalim.halala.core.ui.components.TransactionItemRow
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Radius
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/**
 * Home, from the Home board as far as Phase 0 can fill it: the coin and wordmark, the wallet and
 * the banks in the two summary cards, and the latest transactions. The balance card arrives
 * with budgets, and the review pill with the inbox.
 */
@Composable
fun HomeScreen(onSeeAllClick: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    HomeContent(
        state = state,
        onSettingsClick = viewModel::onSettingsClick,
        onCashClick = viewModel::onCashClick,
        onAccountsClick = viewModel::onAccountsClick,
        onSeeAllClick = onSeeAllClick,
        onTransactionClick = viewModel::onTransactionClick
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onSettingsClick: () -> Unit,
    onCashClick: () -> Unit,
    onAccountsClick: () -> Unit,
    onSeeAllClick: () -> Unit,
    onTransactionClick: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.card)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_halala_coin),
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.logo)
                )
                Text(text = stringResource(R.string.app_name), style = HalalaType.Wordmark)
            }

            Box(
                modifier = Modifier
                    .size(Sizes.touchTarget)
                    .clip(Radius.pill)
                    .clickable(role = Role.Button, onClick = onSettingsClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.settings),
                    tint = HalalaColors.TextMuted,
                    modifier = Modifier.size(Sizes.icon)
                )
            }
        }

        if (state.isLoading) return@Column

        Row(horizontalArrangement = Arrangement.spacedBy(Insets.grid)) {
            SummaryCard(
                label = stringResource(R.string.home_cash),
                amount = state.cashBalance,
                caption = stringResource(R.string.home_cash_count),
                captionColor = HalalaColors.Accent,
                onClick = onCashClick,
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                label = stringResource(R.string.home_banks),
                amount = state.bankBalance,
                caption = pluralStringResource(R.plurals.account_count, state.bankAccountCount, state.bankAccountCount),
                onClick = onAccountsClick,
                modifier = Modifier.weight(1f)
            )
        }

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardLabel(stringResource(R.string.home_recent))
                if (state.recent.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .heightIn(min = Sizes.touchTarget)
                            .clickable(role = Role.Button, onClick = onSeeAllClick),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = stringResource(R.string.see_all),
                            style = HalalaType.Label,
                            color = HalalaColors.Accent
                        )
                    }
                }
            }

            if (state.recent.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_empty),
                    style = HalalaType.Body,
                    color = HalalaColors.TextMuted,
                    modifier = Modifier.padding(top = Spacing.sm)
                )
            }

            state.recent.forEach { item ->
                TransactionItemRow(
                    item = item,
                    withDay = true,
                    divider = true,
                    onClick = { onTransactionClick(item.id) }
                )
            }
        }
    }
}
