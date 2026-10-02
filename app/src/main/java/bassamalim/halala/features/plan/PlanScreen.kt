package bassamalim.halala.features.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
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
import bassamalim.halala.core.ui.components.HalalaCard
import bassamalim.halala.core.ui.components.HalalaChip
import bassamalim.halala.core.ui.components.ScreenTitle
import bassamalim.halala.core.ui.components.SummaryCard
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Spacing
import bassamalim.halala.features.budgets.BudgetRowsList

/**
 * The Plan board, as far as it is built: this pay cycle, its budgets, subscriptions and bills,
 * and the forecast. Savings goals and the calculators fill in the rest.
 */
@Composable
fun PlanScreen(viewModel: PlanViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.card)
    ) {
        ScreenTitle(stringResource(R.string.tab_plan)) {
            if (state.cycle.isNotEmpty()) HalalaChip(label = state.cycle)
        }
        if (state.isLoading) return@Column

        HalalaCard(modifier = Modifier.fillMaxWidth(), onClick = viewModel::onBudgetsClick) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(R.string.budgets_this_cycle), style = HalalaType.Label, color = HalalaColors.TextMuted)
                Text(
                    text = stringResource(if (state.budgets.isEmpty()) R.string.recurring_add else R.string.edit),
                    style = HalalaType.Label,
                    color = HalalaColors.Accent
                )
            }
            if (state.budgets.isEmpty()) Text(
                text = stringResource(R.string.budgets_empty),
                style = HalalaType.Body,
                color = HalalaColors.TextMuted
            ) else BudgetRowsList(state.budgets)
        }

        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Insets.grid)) {
            SummaryCard(
                label = stringResource(R.string.recurring_title),
                amount = stringResource(R.string.recurring_per_month, state.monthly),
                caption = state.next?.let { stringResource(R.string.recurring_plan_next, it.first, it.second) },
                onClick = viewModel::onRecurringClick,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            SummaryCard(
                label = stringResource(R.string.forecast),
                amount = state.endAbout?.let { stringResource(R.string.home_end_about, it) } ?: "—",
                caption = stringResource(R.string.forecast_afford),
                onClick = viewModel::onForecastClick,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        HalalaCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.plan_placeholder), style = HalalaType.Body, color = HalalaColors.TextMuted)
        }
    }
}
