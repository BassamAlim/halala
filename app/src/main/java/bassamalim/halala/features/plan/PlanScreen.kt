package bassamalim.halala.features.plan

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.ui.components.PlaceholderTab
import bassamalim.halala.core.ui.components.SummaryCard

/**
 * Plan, as far as it is built: the Plan board's Subscriptions and bills card. Budgets, goals,
 * the forecast and the calculators arrive in Phases 4 and 5.
 */
@Composable
fun PlanScreen(viewModel: PlanViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    PlaceholderTab(
        title = stringResource(R.string.tab_plan),
        body = stringResource(R.string.plan_placeholder)
    ) {
        SummaryCard(
            label = stringResource(R.string.recurring_title),
            amount = stringResource(R.string.recurring_per_month, state.monthly),
            caption = state.next?.let { stringResource(R.string.recurring_plan_next, it.first, it.second) },
            onClick = viewModel::onRecurringClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
