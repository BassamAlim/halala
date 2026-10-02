package bassamalim.halala.core.widget

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The widget's "+ Cash", waiting for the lock: the app opens on the lock as always, and the
 * transaction form follows once you are in.
 */
@Singleton
class QuickAddRequest @Inject constructor() {
    private val pending = AtomicBoolean(false)

    fun request() = pending.set(true)

    /** Whether one was waiting; it is then gone. */
    fun consume(): Boolean = pending.getAndSet(false)

    companion object {
        const val ACTION = "bassamalim.halala.QUICK_ADD"
    }
}
