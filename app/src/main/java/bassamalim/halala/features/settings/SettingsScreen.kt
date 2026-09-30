package bassamalim.halala.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.ui.components.GroupLabel
import bassamalim.halala.core.ui.components.ListCard
import bassamalim.halala.core.ui.components.ListRow
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/**
 * Settings, from the Settings board, holding only the rows Phase 0 makes true: accounts, backup
 * and export, and the lock. Categories, rules, reminders and AI join as they are built.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.section),
        verticalArrangement = Arrangement.spacedBy(Spacing.card)
    ) {
        TopBar(title = stringResource(R.string.settings), onBack = viewModel::onBackClick)

        Section(stringResource(R.string.settings_money)) {
            ListRow(
                title = stringResource(R.string.accounts),
                subtitle = stringResource(
                    R.string.settings_accounts_summary,
                    pluralStringResource(R.plurals.account_count, state.accountCount, state.accountCount),
                    pluralStringResource(R.plurals.bank_count, state.bankCount, state.bankCount)
                ),
                onClick = viewModel::onAccountsClick
            )
        }

        Section(stringResource(R.string.settings_privacy)) {
            ListRow(
                title = stringResource(R.string.export_title),
                subtitle = stringResource(R.string.settings_export_summary),
                onClick = viewModel::onExportClick
            )
            ListRow(
                title = stringResource(R.string.settings_lock),
                subtitle = pluralStringResource(R.plurals.settings_lock_summary, state.lockMinutes, state.lockMinutes),
                divider = true,
                leading = {
                    Icon(
                        painter = painterResource(R.drawable.ic_lock),
                        contentDescription = null,
                        tint = HalalaColors.TextMuted,
                        modifier = Modifier.size(Sizes.iconSmall)
                    )
                }
            )
        }

        Text(
            text = stringResource(R.string.settings_version, state.version),
            style = HalalaType.Caption,
            color = HalalaColors.TextMuted
        )
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        GroupLabel(label)
        ListCard(Modifier.fillMaxWidth()) { content() }
    }
}
