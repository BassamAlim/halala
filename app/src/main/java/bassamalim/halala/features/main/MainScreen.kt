package bassamalim.halala.features.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import bassamalim.halala.R
import bassamalim.halala.core.ui.components.BottomNav
import bassamalim.halala.core.ui.components.BottomNavItem
import bassamalim.halala.core.ui.components.QuickAddButton
import bassamalim.halala.core.ui.components.ButtonKind
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.components.HalalaSheet
import bassamalim.halala.core.ui.components.rememberLocationRequest
import bassamalim.halala.core.ui.theme.HalalaType
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.features.activity.ActivityScreen
import bassamalim.halala.features.home.HomeScreen
import bassamalim.halala.features.inbox.InboxScreen
import bassamalim.halala.features.plan.PlanScreen
import bassamalim.halala.features.wealth.WealthScreen

/**
 * The tabbed shell. Tabs are local state rather than nav destinations: switching them is not a
 * place you should be able to go "back" to. Quick-add sits on the tabs that list money.
 */
@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {
    var selected by rememberSaveable { mutableStateOf(MainTab.HOME) }
    val askLocation by viewModel.askLocation.collectAsStateWithLifecycle()
    val requestLocation = rememberLocationRequest(viewModel::onLocationAnswered)

    if (askLocation) HalalaSheet(viewModel::onLocationAnswered) {
        Text(text = stringResource(R.string.location_ask_title), style = HalalaType.Title)
        Text(text = stringResource(R.string.location_ask_body), style = HalalaType.Body, color = HalalaColors.TextMuted)
        HalalaButton(stringResource(R.string.location_ask_allow), requestLocation, Modifier.fillMaxWidth(), kind = ButtonKind.Primary)
        HalalaButton(stringResource(R.string.location_ask_later), viewModel::onLocationAnswered, Modifier.fillMaxWidth())
    }

    Scaffold(
        containerColor = HalalaColors.Bg,
        bottomBar = {
            BottomNav(
                items = MainTab.entries.map { BottomNavItem(stringResource(it.label), it.icon) },
                selectedIndex = selected.ordinal,
                onSelect = { selected = MainTab.entries[it] }
            )
        },
        floatingActionButton = {
            if (selected == MainTab.HOME || selected == MainTab.ACTIVITY) {
                QuickAddButton(
                    contentDescription = stringResource(R.string.quick_add),
                    onClick = viewModel::onQuickAddClick
                )
            }
        }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // The keyboard covers the nav; only the content makes room for it.
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            when (selected) {
                MainTab.HOME -> HomeScreen(onSeeAllClick = { selected = MainTab.ACTIVITY })
                MainTab.ACTIVITY -> ActivityScreen()
                MainTab.INBOX -> InboxScreen()
                MainTab.PLAN -> PlanScreen()
                MainTab.WEALTH -> WealthScreen()
            }
        }
    }
}

enum class MainTab(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    HOME(R.string.tab_home, R.drawable.ic_home),
    ACTIVITY(R.string.tab_activity, R.drawable.ic_activity),
    INBOX(R.string.tab_inbox, R.drawable.ic_inbox),
    PLAN(R.string.tab_plan, R.drawable.ic_plan),
    WEALTH(R.string.tab_wealth, R.drawable.ic_wealth)
}
