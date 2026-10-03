package bassamalim.halala.features.alerts

import bassamalim.halala.core.ui.components.Skeleton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.components.HalalaCard
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/**
 * Anomaly alerts (the spec's list): possible duplicates, unusually large charges, foreign
 * charges, declined cards, balances that don't add up, and a bank whose SMS stopped parsing. Each opens its transaction, and can be
 * dismissed (or, for a large one, called normal for its merchant). No board draws it.
 */
@Composable
fun AlertsScreen(viewModel: AlertsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.section),
        verticalArrangement = Arrangement.spacedBy(Spacing.card)
    ) {
        TopBar(title = stringResource(R.string.alerts), onBack = viewModel::onBackClick)
        if (state.isLoading) {
            Skeleton()
            return@Column
        }
        if (state.alerts.isEmpty()) Text(text = stringResource(R.string.alerts_empty), style = HalalaType.Body, color = HalalaColors.TextMuted)

        state.alerts.forEach { alert ->
            HalalaCard(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_warning),
                        contentDescription = null,
                        tint = if (alert.kind == AlertKind.MISMATCH || alert.kind == AlertKind.DUPLICATE) HalalaColors.StateOver else HalalaColors.StateWarn,
                        modifier = Modifier.size(Sizes.iconSmall)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Text(text = stringResource(titleOf(alert.kind)), style = HalalaType.BodyStrong)
                        Text(text = body(alert), style = HalalaType.Label, color = HalalaColors.TextMuted)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    alert.transactionId?.let { id -> HalalaButton(stringResource(R.string.recurring_open), { viewModel.onOpen(id) }) }
                    alert.merchantId?.let { id -> HalalaButton(stringResource(R.string.alert_normal), { viewModel.onNormal(id) }) }
                    HalalaButton(stringResource(R.string.alert_dismiss), { viewModel.onDismiss(alert.key) })
                }
            }
        }
    }
}

private fun titleOf(kind: AlertKind) = when (kind) {
    AlertKind.DUPLICATE -> R.string.alert_duplicate
    AlertKind.LARGE -> R.string.alert_large
    AlertKind.FOREIGN -> R.string.alert_foreign
    AlertKind.DECLINED -> R.string.alert_declined
    AlertKind.MISMATCH -> R.string.alert_mismatch
    AlertKind.PARSER -> R.string.alert_parser
}

@Composable
private fun body(alert: AlertItem): String = when (alert.kind) {
    AlertKind.DUPLICATE -> stringResource(R.string.alert_duplicate_body, alert.name, alert.amount, alert.day)
    AlertKind.LARGE -> stringResource(R.string.alert_large_body, alert.name, alert.amount, alert.other, alert.day)
    AlertKind.FOREIGN -> stringResource(R.string.alert_foreign_body, alert.name, alert.amount, alert.other, alert.day)
    AlertKind.DECLINED -> stringResource(R.string.alert_declined_body, alert.name, alert.day)
    AlertKind.MISMATCH -> stringResource(R.string.alert_mismatch_body, alert.name, alert.other, alert.amount, alert.day)
    AlertKind.PARSER -> stringResource(R.string.alert_parser_body, alert.name, alert.amount, alert.other)
}
