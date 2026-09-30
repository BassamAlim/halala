package bassamalim.halala.core.nav

import androidx.navigation.NavOptionsBuilder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Navigation that ViewModels can ask for without holding a [androidx.navigation.NavController]
 * (which would leak the graph into the DI container). Commands are buffered in a channel, so one
 * issued while the app is backgrounded is delivered when navigation resumes, and delivered once.
 */
@Singleton
class Navigator @Inject constructor() {

    private val commands = Channel<NavCommand>(capacity = Channel.BUFFERED)
    val events = commands.receiveAsFlow()

    fun navigate(destination: Screen, options: NavOptionsBuilder.() -> Unit = {}) {
        commands.trySend(NavCommand.To(destination, options))
    }

    fun popBackStack() {
        commands.trySend(NavCommand.Back)
    }
}

sealed interface NavCommand {
    data class To(val destination: Screen, val options: NavOptionsBuilder.() -> Unit) : NavCommand
    data object Back : NavCommand
}
