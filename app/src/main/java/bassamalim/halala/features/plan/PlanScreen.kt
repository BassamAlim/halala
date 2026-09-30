package bassamalim.halala.features.plan

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import bassamalim.halala.R
import bassamalim.halala.core.ui.components.PlaceholderTab

/** Budgets, goals, subscriptions, forecast and calculators arrive in Phases 3–5. */
@Composable
fun PlanScreen() {
    PlaceholderTab(
        title = stringResource(R.string.tab_plan),
        body = stringResource(R.string.plan_placeholder)
    )
}
