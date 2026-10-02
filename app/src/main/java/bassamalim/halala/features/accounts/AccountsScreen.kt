package bassamalim.halala.features.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.enums.AmountTone
import bassamalim.halala.core.ui.accountTypeLabel
import bassamalim.halala.core.ui.components.Avatar
import bassamalim.halala.core.ui.components.GroupLabel
import bassamalim.halala.core.ui.components.ListCard
import bassamalim.halala.core.ui.components.ListRow
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Spacing

/**
 * Every account you named, the wallet first, with its balance. The board has no Accounts
 * screen, so it borrows the Settings list: rows in a card, bank and last four digits under the
 * name, as on the onboarding board.
 */
@Composable
fun AccountsScreen(viewModel: AccountsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.section),
        verticalArrangement = Arrangement.spacedBy(Spacing.card)
    ) {
        TopBar(
            title = stringResource(R.string.accounts),
            onBack = viewModel::onBackClick,
            actionLabel = stringResource(R.string.add),
            onAction = viewModel::onAddClick
        )

        if (state.isLoading) return@Column

        AccountList(state.active, onClick = viewModel::onAccountClick)

        if (state.archived.isNotEmpty()) {
            Column {
                GroupLabel(stringResource(R.string.accounts_archived))
                AccountList(state.archived, muted = true, onClick = viewModel::onAccountClick)
            }
        }
    }
}

@Composable
private fun AccountList(rows: List<AccountRow>, onClick: (Long) -> Unit, muted: Boolean = false) {
    // An archived account is muted whole: name and balance, not just the figure.
    ListCard(Modifier.fillMaxWidth()) {
        CompositionLocalProvider(LocalContentColor provides if (muted) HalalaColors.TextMuted else HalalaColors.Text) {
            rows.forEachIndexed { index, row ->
                val type = accountTypeLabel(row.type)
                val where = listOfNotNull(row.institution, row.last4).joinToString(" ")

                ListRow(
                    title = row.name,
                    subtitle = if (where.isEmpty()) type else stringResource(R.string.meta_pair, where, type),
                    divider = index > 0,
                    leading = { Avatar(initial = row.initial, tone = AmountTone.Spending) },
                    trailing = {
                        Text(text = row.balance, style = HalalaNumbers.Amount)
                    },
                    onClick = { onClick(row.id) }
                )
            }
        }
    }
}
