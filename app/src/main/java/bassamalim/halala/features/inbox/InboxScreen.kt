package bassamalim.halala.features.inbox

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
import bassamalim.halala.core.ui.components.ListCard
import bassamalim.halala.core.ui.components.ListRow
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/**
 * The Inbox, behind Home's icon (no board: the system's list card): everything waiting for your say, one row a
 * kind, each opening the screen where it is answered.
 */
@Composable
fun InboxScreen(viewModel: InboxViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.section),
        verticalArrangement = Arrangement.spacedBy(Spacing.card)
    ) {
        TopBar(title = stringResource(R.string.inbox), onBack = viewModel::onBackClick)
        if (state.isLoading) return@Column

        if (state.rows.isEmpty()) {
            Text(text = stringResource(R.string.inbox_empty), style = HalalaType.Body, color = HalalaColors.TextMuted)
        } else ListCard(Modifier.fillMaxWidth()) {
            state.rows.forEachIndexed { index, row ->
                ListRow(
                    title = pluralStringResource(title(row.kind), row.count, row.count),
                    subtitle = stringResource(hint(row.kind)),
                    divider = index > 0,
                    trailing = {
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = HalalaColors.TextMuted,
                            modifier = Modifier.size(Sizes.iconSmall)
                        )
                    },
                    onClick = { viewModel.onRowClick(row.kind) }
                )
            }
        }
    }
}

private fun title(kind: InboxKind) = when (kind) {
    InboxKind.MERCHANTS -> R.plurals.inbox_merchants
    InboxKind.SAME_MERCHANT -> R.plurals.inbox_same_merchant
    InboxKind.ALERTS -> R.plurals.inbox_alerts
    InboxKind.RECURRING -> R.plurals.inbox_recurring
    InboxKind.PEOPLE -> R.plurals.inbox_people
    InboxKind.TRIPS -> R.plurals.inbox_trips
}

private fun hint(kind: InboxKind) = when (kind) {
    InboxKind.MERCHANTS -> R.string.inbox_merchants_hint
    InboxKind.SAME_MERCHANT -> R.string.inbox_people_hint
    InboxKind.ALERTS -> R.string.inbox_alerts_hint
    InboxKind.RECURRING -> R.string.inbox_recurring_hint
    InboxKind.PEOPLE -> R.string.inbox_people_hint
    InboxKind.TRIPS -> R.string.inbox_trips_hint
}
