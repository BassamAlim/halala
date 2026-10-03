package bassamalim.halala.core.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A Save button's guard: a second tap while the first is still writing does nothing, so a
 * double tap never writes twice. [block] returns true when the screen is done (it saved and is
 * leaving), which keeps every later tap out too; false (problems to fix) lets the next one in.
 * Main thread only, as clicks are.
 */
class OneAtATime {
    private var busy = false

    fun launch(scope: CoroutineScope, block: suspend () -> Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            var done = false
            try {
                done = block()
            } finally {
                busy = done
            }
        }
    }
}
