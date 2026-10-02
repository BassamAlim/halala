package bassamalim.halala.features.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.ui.components.HalalaCard
import bassamalim.halala.core.ui.components.HalalaChip
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Spacing

/**
 * Every budget this pay cycle, with what it has spent: the Plan board's budgets card, full size.
 * No board draws this screen.
 */
@Composable
fun BudgetsScreen(viewModel: BudgetsViewModel = hiltViewModel()) {
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
            title = stringResource(R.string.budgets),
            onBack = viewModel::onBackClick,
            actionLabel = stringResource(R.string.recurring_add),
            onAction = viewModel::onAddClick
        )
        if (state.isLoading) return@Column

        HalalaChip(label = state.cycle)
        if (state.rows.isEmpty()) Text(
            text = stringResource(R.string.budgets_empty),
            style = HalalaType.Body,
            color = HalalaColors.TextMuted
        ) else HalalaCard(modifier = Modifier.fillMaxWidth()) {
            BudgetRowsList(state.rows, viewModel::onBudgetClick)
        }
        Text(text = stringResource(R.string.budgets_hint), style = HalalaType.Caption, color = HalalaColors.TextMuted)
    }
}
