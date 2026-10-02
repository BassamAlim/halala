package bassamalim.halala.features.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import bassamalim.halala.core.ui.components.CardLabel
import bassamalim.halala.core.ui.components.HalalaCard
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
 * Home, from the Home board as far as it can be filled: the mark and wordmark, the review pill
 * while anything waits in the inbox, the wallet and the banks in the two summary cards, what
 * people owe you, what is coming up, and the latest transactions. The balance card arrives with
 * budgets.
 */
@Composable
fun HomeScreen(onSeeAllClick: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    HomeContent(
        state = state,
        onReviewClick = viewModel::onReviewClick,
        onSettingsClick = viewModel::onSettingsClick,
        onCashClick = viewModel::onCashClick,
        onAccountsClick = viewModel::onAccountsClick,
        onSeeAllClick = onSeeAllClick,
        onTransactionClick = viewModel::onTransactionClick,
        onPeopleClick = viewModel::onPeopleClick,
        onComingUpClick = viewModel::onComingUpClick
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onReviewClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCashClick: () -> Unit,
    onAccountsClick: () -> Unit,
    onSeeAllClick: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    onPeopleClick: () -> Unit = {},
    onComingUpClick: () -> Unit = {}
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
                if (state.reviewCount > 0) ReviewPill(state.reviewCount, onReviewClick)

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

/** The board's "7 to review" pill; its hit area is padded out to a touch target. */
@Composable
private fun ReviewPill(count: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = Sizes.touchTarget)
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .height(Sizes.pill)
                .clip(Radius.pill)
                .background(HalalaColors.Surface)
                .border(Sizes.border, HalalaColors.Line, Radius.pill)
                .padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_inbox),
                contentDescription = null,
                tint = HalalaColors.Text,
                modifier = Modifier.size(Sizes.iconSmall)
            )
            Text(
                text = pluralStringResource(R.plurals.review_count, count, count),
                style = HalalaType.Label,
                color = HalalaColors.Text
            )
        }
    }
}
