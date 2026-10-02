package bassamalim.halala.features.home

import bassamalim.halala.core.ui.components.SkeletonRows
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import bassamalim.halala.core.Globals
import bassamalim.halala.core.ui.components.BalanceCard
import bassamalim.halala.core.ui.components.CardLabel
import bassamalim.halala.core.domain.BudgetState
import bassamalim.halala.core.ui.components.HalalaCard
import bassamalim.halala.core.ui.components.ListCard
import bassamalim.halala.core.ui.components.ListRow
import bassamalim.halala.core.ui.components.SummaryCard
import bassamalim.halala.core.ui.components.TransactionItemRow
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Radius
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/**
 * Home, from the Home board as far as it can be filled: the mark and wordmark, Ask and Settings
 * (the board's review pill became the Inbox tab), the wallet and the banks in the two summary cards, what
 * people owe you, what is coming up, and the latest transactions, under the balance card once
 * there is a budget for everything.
 */
@Composable
fun HomeScreen(onSeeAllClick: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    HomeContent(
        state = state,
        onSettingsClick = viewModel::onSettingsClick,
        onAskClick = viewModel::onAskClick,
        onCashClick = viewModel::onCashClick,
        onAccountsClick = viewModel::onAccountsClick,
        onSeeAllClick = onSeeAllClick,
        onTransactionClick = viewModel::onTransactionClick,
        onPeopleClick = viewModel::onPeopleClick,
        onComingUpClick = viewModel::onComingUpClick,
        onAlertsClick = viewModel::onAlertsClick
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onSettingsClick: () -> Unit,
    onAskClick: () -> Unit,
    onCashClick: () -> Unit,
    onAccountsClick: () -> Unit,
    onSeeAllClick: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    onPeopleClick: () -> Unit = {},
    onComingUpClick: () -> Unit = {},
    onAlertsClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(
                top = Insets.screenTop,
                // Clear of the quick-add button.
                bottom = Sizes.fab + Spacing.section
            ),
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
                    painter = painterResource(R.drawable.ic_halala_mark),
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.logo)
                )
                Text(text = stringResource(R.string.app_name), style = HalalaType.Wordmark)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(Sizes.touchTarget)
                        .clip(Radius.pill)
                        .clickable(role = Role.Button, onClick = onAskClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_assistant),
                        contentDescription = stringResource(R.string.assistant_title),
                        tint = HalalaColors.TextMuted,
                        modifier = Modifier.size(Sizes.icon)
                    )
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
        }

        if (state.alertCount > 0) ListCard(Modifier.fillMaxWidth()) {
            ListRow(
                title = pluralStringResource(R.plurals.alert_count, state.alertCount, state.alertCount),
                subtitle = stringResource(R.string.alerts_hint),
                onClick = onAlertsClick
            )
        }

        state.balance?.let { balance ->
            val days = pluralStringResource(R.plurals.home_days_left, balance.daysLeft, balance.daysLeft)
            BalanceCard(
                overline = stringResource(R.string.home_spent_cycle),
                status = stringResource(
                    when (balance.state) {
                        BudgetState.OK -> R.string.home_on_track
                        BudgetState.WARN -> R.string.home_spending_fast
                        BudgetState.OVER -> R.string.home_over_budget
                    }
                ),
                spent = balance.spent,
                currency = Globals.PRIMARY_CURRENCY,
                progress = balance.progress,
                state = balance.state,
                footStart = balance.over?.let { stringResource(R.string.home_over_amount, it, days) }
                    ?: stringResource(R.string.home_of_budget, balance.limit, days),
                footEnd = balance.endAbout?.let { stringResource(R.string.home_end_about, it) }.orEmpty()
            )
        }

        // While loading, the cards keep their shape with blank figures, so nothing jumps in.
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(Insets.grid)
        ) {
            SummaryCard(
                label = stringResource(R.string.home_cash),
                amount = state.cashBalance,
                currency = Globals.PRIMARY_CURRENCY,
                caption = stringResource(R.string.home_cash_count),
                captionColor = HalalaColors.Accent,
                onClick = onCashClick,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            SummaryCard(
                label = stringResource(R.string.home_banks),
                amount = state.bankBalance,
                currency = Globals.PRIMARY_CURRENCY,
                caption = if (state.isLoading) ""
                else pluralStringResource(R.plurals.account_count, state.bankAccountCount, state.bankAccountCount),
                onClick = onAccountsClick,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        if (state.hasLoans) SummaryCard(
            label = stringResource(R.string.home_people_owe),
            amount = state.owedToYou,
            currency = Globals.PRIMARY_CURRENCY,
            caption = stringResource(R.string.home_you_owe, state.youOwe),
            onClick = onPeopleClick,
            modifier = Modifier.fillMaxWidth()
        )

        if (state.comingUp.isNotEmpty()) HalalaCard(label = stringResource(R.string.home_coming_up), onClick = onComingUpClick) {
            state.comingUp.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = item.name, style = HalalaType.Label, modifier = Modifier.weight(1f))
                    Text(text = "${item.due} · ", style = HalalaType.Label, color = HalalaColors.TextMuted)
                    Text(text = item.amount, style = HalalaNumbers.Meta, color = HalalaColors.Text)
                }
            }
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
                            .clip(Radius.sm)
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

            if (!state.isLoading && state.recent.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_empty),
                    style = HalalaType.Body,
                    color = HalalaColors.TextMuted,
                    modifier = Modifier.padding(top = Spacing.sm)
                )
            }

            if (state.isLoading) SkeletonRows()

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
