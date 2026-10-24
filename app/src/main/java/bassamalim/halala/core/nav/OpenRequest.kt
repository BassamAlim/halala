package bassamalim.halala.core.nav

import android.content.Context
import android.content.Intent
import bassamalim.halala.core.Activity
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A screen asked for from outside (the widget's "+ Cash", a notification), waiting for the lock:
 * the app opens on the lock as always, and the screen follows once you are in.
 */
@Singleton
class OpenRequest @Inject constructor() {
    private val pending = AtomicReference<Screen?>(null)

    /** Takes the screen [intent] asks for, if it asks for one. */
    fun request(intent: Intent?) {
        val screen = when (intent?.action) {
            QUICK_ADD -> Screen.EditTransaction()
            OPEN -> intent.getStringExtra(EXTRA_SCREEN)
                ?.let { runCatching { Json.decodeFromString(Screen.serializer(), it) }.getOrNull() }
            else -> null
        } ?: return
        pending.set(screen)
    }

    /** The screen that was waiting, if any; it is then gone. */
    fun consume(): Screen? = pending.getAndSet(null)

    companion object {
        const val QUICK_ADD = "bassamalim.halala.QUICK_ADD"
        private const val OPEN = "bassamalim.halala.OPEN"
        private const val EXTRA_SCREEN = "screen"

        /** Opens the app on [screen], behind the lock; brings a running app forward rather than starting another. */
        fun intent(context: Context, screen: Screen): Intent =
            Intent(context, Activity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .setAction(OPEN)
                .putExtra(EXTRA_SCREEN, Json.encodeToString(Screen.serializer(), screen))
    }
}
