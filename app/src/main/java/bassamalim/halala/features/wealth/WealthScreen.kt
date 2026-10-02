package bassamalim.halala.features.wealth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.Globals
import bassamalim.halala.core.ui.components.ListCard
import bassamalim.halala.core.ui.components.ListRow
import bassamalim.halala.core.ui.components.PlaceholderTab
import bassamalim.halala.core.ui.components.SummaryCard

/**
 * Wealth, until Phase 5 builds net worth, savings, funds and gold: the accounts and the people
 * you send money to, which already work, and a note on what's coming.
 */
@Composable
fun WealthScreen(viewModel: WealthViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    PlaceholderTab(
        title = stringResource(R.string.tab_wealth),
        body = stringResource(R.string.wealth_placeholder)
    ) {
        SummaryCard(
            label = stringResource(R.string.wealth_accounts),
            amount = state.accountsTotal,
            currency = Globals.PRIMARY_CURRENCY,
            caption = pluralStringResource(R.plurals.account_count, state.accountCount, state.accountCount),
            onClick = viewModel::onAccountsClick,
            modifier = Modifier.fillMaxWidth()
        )
        ListCard(Modifier.fillMaxWidth()) {
            ListRow(
                title = stringResource(R.string.people),
                subtitle = pluralStringResource(R.plurals.people_count, state.peopleCount, state.peopleCount),
                onClick = viewModel::onPeopleClick
            )
        }
    }
}
