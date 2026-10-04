package bassamalim.halala.core

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import bassamalim.halala.core.ui.ground
import bassamalim.halala.core.ui.LocalMerchantLogos
import bassamalim.halala.core.ui.MerchantLogos
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import bassamalim.halala.core.lock.LockManager
import bassamalim.halala.core.widget.HalalaWidget
import bassamalim.halala.core.widget.QuickAddRequest
import kotlinx.coroutines.launch
import bassamalim.halala.core.nav.Navigation
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.ui.theme.HalalaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** A [FragmentActivity] because that's what BiometricPrompt attaches to. */
@AndroidEntryPoint
class Activity : FragmentActivity() {

    @Inject lateinit var navigator: Navigator
    @Inject lateinit var lockManager: LockManager
    @Inject lateinit var quickAdd: QuickAddRequest
    @Inject lateinit var merchantLogos: MerchantLogos

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is dark-only, so the system bars are told so rather than asked.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent?.action == QuickAddRequest.ACTION) quickAdd.request()

        // No screenshots, and a blank card in recent apps: this is a ledger.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            val logos by merchantLogos.images.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalMerchantLogos provides logos) {
                HalalaTheme {
                    // The top and sides are kept clear here, once. The bottom is each screen's: the
                    // tabs draw their nav behind the gesture bar, sub-screens stop above it and the
                    // keyboard (see NavGraph).
                    Box(
                        Modifier
                            .fillMaxSize()
                            .ground()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    ) {
                        // Every cold start opens on the lock; nothing is on screen before it.
                        Navigation(navigator = navigator, startDestination = Screen.Lock(resumable = false))
                    }
                }
            }
        }
    }

    /** The widget's "+ Cash" while the app is open: the form now, or after the lock. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == QuickAddRequest.ACTION) quickAdd.request()
    }

    override fun onStart() {
        super.onStart()

        if (lockManager.shouldLockOnResume(SystemClock.elapsedRealtime())) {
            lockManager.onLocked()
            navigator.navigate(Screen.Lock(resumable = true)) { launchSingleTop = true }
        } else if (!lockManager.isLocked() && quickAdd.consume()) {
            navigator.navigate(Screen.EditTransaction())
        }
    }

    override fun onStop() {
        super.onStop()

        lockManager.onBackgrounded(SystemClock.elapsedRealtime())
        lifecycleScope.launch { HalalaWidget.refresh(applicationContext) }
    }
}
