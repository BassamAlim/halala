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
import bassamalim.halala.features.editAccount.EditAccountScreen
import bassamalim.halala.features.editTransaction.EditTransactionScreen
import bassamalim.halala.features.export.ExportScreen
import bassamalim.halala.features.lock.LockScreen
import bassamalim.halala.features.main.MainScreen
import bassamalim.halala.features.onboarding.OnboardingScreen
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
