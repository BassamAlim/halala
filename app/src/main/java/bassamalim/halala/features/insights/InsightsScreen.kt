package bassamalim.halala.features.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import bassamalim.halala.R
import bassamalim.halala.core.ui.components.ScreenTitle
import bassamalim.halala.core.ui.components.SegmentedControl
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Spacing
import bassamalim.halala.features.moneyFlow.MoneyFlowContent

/**
 * The Insights tab (no board): Spending (the charts and where you spend, [InsightsContent]) or
 * Money flow, picked beside the title.
 */
@Composable
fun InsightsScreen() {
    var view by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.section),
        verticalArrangement = Arrangement.spacedBy(Spacing.card)
    ) {
        ScreenTitle(stringResource(R.string.tab_insights)) {
            SegmentedControl(
                options = listOf(stringResource(R.string.insights_spending), stringResource(R.string.activity_money_flow)),
                selectedIndex = view,
                onSelect = { view = it }
            )
        }
        if (view == 0) InsightsContent() else MoneyFlowContent()
    }
}
