package bassamalim.halala.features.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import bassamalim.halala.core.ui.Emphasized
import bassamalim.halala.core.ui.settle
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
import bassamalim.halala.features.insights.InsightsScreen
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
        containerColor = Color.Transparent,
        bottomBar = {
            BottomNav(
                items = MainTab.entries.map { BottomNavItem(stringResource(it.label), it.icon) },
                selectedIndex = selected.ordinal,
                onSelect = { selected = MainTab.entries[it] }
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = selected == MainTab.HOME || selected == MainTab.ACTIVITY,
                enter = scaleIn(settle()) + fadeIn(),
                exit = scaleOut(tween(TAB_OUT_MS)) + fadeOut(tween(TAB_OUT_MS))
            ) {
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
            // Fade through: the old tab steps back and goes, the new one rises into its place.
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    (fadeIn(tween(TAB_IN_MS, delayMillis = TAB_OUT_MS, easing = Emphasized)) +
                        scaleIn(tween(TAB_IN_MS, delayMillis = TAB_OUT_MS, easing = Emphasized), initialScale = 0.97f))
                        .togetherWith(fadeOut(tween(TAB_OUT_MS)))
                },
                label = "tab"
            ) { tab ->
                when (tab) {
                    MainTab.HOME -> HomeScreen(onSeeAllClick = { selected = MainTab.ACTIVITY })
                    MainTab.ACTIVITY -> ActivityScreen()
                    MainTab.INSIGHTS -> InsightsScreen()
                    MainTab.PLAN -> PlanScreen()
                    MainTab.WEALTH -> WealthScreen()
                }
            }
        }
    }
}

private const val TAB_OUT_MS = 90
private const val TAB_IN_MS = 260

enum class MainTab(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    HOME(R.string.tab_home, R.drawable.ic_home),
    ACTIVITY(R.string.tab_activity, R.drawable.ic_activity),
    INSIGHTS(R.string.tab_insights, R.drawable.ic_insights),
    PLAN(R.string.tab_plan, R.drawable.ic_plan),
    WEALTH(R.string.tab_wealth, R.drawable.ic_wealth)
}
