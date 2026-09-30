package bassamalim.halala.core.nav

import androidx.compose.runtime.Composable
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
import bassamalim.halala.features.editAccount.EditAccountScreen
import bassamalim.halala.features.editTransaction.EditTransactionScreen
import bassamalim.halala.features.export.ExportScreen
import bassamalim.halala.features.lock.LockScreen
import bassamalim.halala.features.main.MainScreen
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
        composable<Screen.Lock> { LockScreen() }

        composable<Screen.Main> { MainScreen() }

        composable<Screen.Accounts> { AccountsScreen() }

        composable<Screen.EditAccount> { EditAccountScreen() }

        composable<Screen.Transaction> { TransactionScreen() }

        composable<Screen.EditTransaction> { EditTransactionScreen() }

        composable<Screen.ReconcileCash> { ReconcileCashScreen() }

        composable<Screen.Settings> { SettingsScreen() }

        composable<Screen.Export> { ExportScreen() }
    }
}
