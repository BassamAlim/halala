package bassamalim.halala.core.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import bassamalim.halala.core.ui.inFromRight
import bassamalim.halala.core.ui.inFromLeft
import bassamalim.halala.core.ui.outToLeft
import bassamalim.halala.core.ui.outToRight
import bassamalim.halala.features.accounts.AccountsScreen
import bassamalim.halala.features.categories.CategoriesScreen
import bassamalim.halala.features.editRule.EditRuleScreen
import bassamalim.halala.features.history.HistoryScreen
import bassamalim.halala.features.editAccount.EditAccountScreen
import bassamalim.halala.features.editTransaction.EditTransactionScreen
import bassamalim.halala.features.export.ExportScreen
import bassamalim.halala.features.lock.LockScreen
import bassamalim.halala.features.main.MainScreen
import bassamalim.halala.features.merchant.MerchantScreen
import bassamalim.halala.features.merchants.MerchantsScreen
import bassamalim.halala.features.people.PeopleScreen
import bassamalim.halala.features.budgets.BudgetsScreen
import bassamalim.halala.features.forecast.ForecastScreen
import bassamalim.halala.features.alerts.AlertsScreen
import bassamalim.halala.features.assets.AssetsScreen
import bassamalim.halala.features.editAsset.EditAssetScreen
import bassamalim.halala.features.zakat.ZakatScreen
import bassamalim.halala.features.retirement.CompoundScreen
import bassamalim.halala.features.savings.SavingsScreen
import bassamalim.halala.features.backup.BackupScreen
import bassamalim.halala.features.tags.EditTagScreen
import bassamalim.halala.features.tags.TagsScreen
import bassamalim.halala.features.savings.SavingsTermsScreen
import bassamalim.halala.features.retirement.RetirementScreen
import bassamalim.halala.features.digest.DigestScreen
import bassamalim.halala.features.digest.DigestsScreen
import bassamalim.halala.features.editGoal.EditGoalScreen
import bassamalim.halala.features.editBudget.EditBudgetScreen
import bassamalim.halala.features.recurring.RecurringScreen
import bassamalim.halala.features.editRecurring.EditRecurringScreen
import bassamalim.halala.features.person.PersonScreen
import bassamalim.halala.features.onboarding.OnboardingScreen
import bassamalim.halala.features.review.ReviewScreen
import bassamalim.halala.features.rules.RulesScreen
import bassamalim.halala.features.reconcileCash.ReconcileCashScreen
import bassamalim.halala.features.settings.SettingsScreen
import bassamalim.halala.features.transaction.TransactionScreen

@Composable
fun Navigation(navigator: Navigator, startDestination: Screen) {
    val navController = rememberNavController()

    LaunchedEffect(navController) {
        navigator.events.collect { command ->
            when (command) {
                is NavCommand.To -> navController.navigate(command.destination, command.options)
                is NavCommand.Back -> navController.popBackStack()
            }
        }
    }

    NavGraph(navController = navController, startDestination = startDestination)
}

@Composable
fun NavGraph(navController: NavHostController, startDestination: Screen) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = inFromRight,
        exitTransition = outToLeft,
        popEnterTransition = inFromLeft,
        popExitTransition = outToRight
    ) {
        screen<Screen.Lock> { LockScreen() }

        composable<Screen.Main> { MainScreen() }

        screen<Screen.Accounts> { AccountsScreen() }

        screen<Screen.EditAccount> { EditAccountScreen() }

        screen<Screen.Transaction> { TransactionScreen() }

        screen<Screen.EditTransaction> { EditTransactionScreen() }

        screen<Screen.ReconcileCash> { ReconcileCashScreen() }

        screen<Screen.Settings> { SettingsScreen() }

        screen<Screen.Export> { ExportScreen() }

        screen<Screen.Onboarding> { OnboardingScreen() }

        screen<Screen.Review> { ReviewScreen() }

        screen<Screen.Rules> { RulesScreen() }

        screen<Screen.Categories> { CategoriesScreen() }

        screen<Screen.Merchants> { MerchantsScreen() }

        screen<Screen.Merchant> { MerchantScreen() }

        screen<Screen.People> { PeopleScreen() }

        screen<Screen.Person> { PersonScreen() }

        screen<Screen.Recurring> { RecurringScreen() }

        screen<Screen.EditRecurring> { EditRecurringScreen() }

        screen<Screen.Budgets> { BudgetsScreen() }

        screen<Screen.Forecast> { ForecastScreen() }

        screen<Screen.Alerts> { AlertsScreen() }

        screen<Screen.Assets> { AssetsScreen() }

        screen<Screen.EditAsset> { EditAssetScreen() }

        screen<Screen.Zakat> { ZakatScreen() }

        screen<Screen.Retirement> { RetirementScreen() }

        screen<Screen.Compound> { CompoundScreen() }

        screen<Screen.Savings> { SavingsScreen() }

        screen<Screen.Backup> { BackupScreen() }

        screen<Screen.Tags> { TagsScreen() }

        screen<Screen.EditTag> { EditTagScreen() }

        screen<Screen.SavingsTerms> { SavingsTermsScreen() }

        screen<Screen.Digest> { DigestScreen() }

        screen<Screen.Digests> { DigestsScreen() }

        screen<Screen.EditGoal> { EditGoalScreen() }

        screen<Screen.EditBudget> { EditBudgetScreen() }

        screen<Screen.EditRule> { EditRuleScreen() }

        screen<Screen.History> { HistoryScreen() }
    }
}

/**
 * A destination that stops above the gesture bar and the keyboard. [Screen.Main] is the one
 * that doesn't: its nav draws behind the gesture bar and stays put under the keyboard.
 */
private inline fun <reified T : Any> NavGraphBuilder.screen(noinline content: @Composable () -> Unit) =
    composable<T> {
        Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) { content() }
    }
