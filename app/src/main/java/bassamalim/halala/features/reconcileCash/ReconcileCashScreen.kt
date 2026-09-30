package bassamalim.halala.features.reconcileCash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.ui.components.FormField
import bassamalim.halala.core.ui.components.HalalaTextField
import bassamalim.halala.core.ui.components.SummaryCard
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Spacing

/**
 * Counting the wallet: type what's in it, see what the gap will be recorded as, save. The
 * wallet's balance is always yours to correct this way.
 */
@Composable
fun ReconcileCashScreen(viewModel: ReconcileCashViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.section),
        verticalArrangement = Arrangement.spacedBy(Spacing.section)
    ) {
        TopBar(
            title = stringResource(R.string.reconcile_title),
            onBack = viewModel::onBackClick,
            actionLabel = stringResource(R.string.save),
            actionEnabled = !state.isLoading,
            onAction = viewModel::onSaveClick
        )

        if (state.isLoading) return@Column

        SummaryCard(
            label = stringResource(R.string.reconcile_recorded),
            amount = "${state.recorded} ${state.currency}",
            modifier = Modifier.fillMaxWidth()
        )

        FormField(
            label = stringResource(R.string.reconcile_counted),
            error = stringResource(R.string.amount_invalid).takeIf { state.isInvalid },
            hint = when (val outcome = state.outcome) {
                null -> stringResource(R.string.reconcile_hint)
                Outcome.Matches -> stringResource(R.string.reconcile_matches)
                is Outcome.Spent -> stringResource(R.string.reconcile_spent, outcome.amount)
                is Outcome.Found -> stringResource(R.string.reconcile_found, outcome.amount)
            }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                HalalaTextField(
                    value = state.counted,
                    onValueChange = viewModel::onCountedChange,
                    placeholder = "0.00",
                    numeric = true,
                    textStyle = HalalaNumbers.AmountXl,
                    isError = state.isInvalid,
                    modifier = Modifier.weight(1f)
                )
                Text(text = state.currency, style = HalalaType.Title, color = HalalaColors.TextMuted)
            }
        }
    }
}
