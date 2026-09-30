package bassamalim.halala.core

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import bassamalim.halala.core.lock.LockManager
import bassamalim.halala.core.nav.Navigation
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** A [FragmentActivity] because that's what BiometricPrompt attaches to. */
@AndroidEntryPoint
class Activity : FragmentActivity() {

    @Inject lateinit var navigator: Navigator
    @Inject lateinit var lockManager: LockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is dark-only, so the system bars are told so rather than asked.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        super.onCreate(savedInstanceState)

        // No screenshots, and a blank card in recent apps: this is a ledger.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            HalalaTheme {
                // safeDrawing keeps content out from under the status bar and gesture area.
                // Applying it here consumes the insets, so the Scaffolds further down don't pad
                // a second time.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(HalalaColors.Bg)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    // Every cold start opens on the lock; nothing is on screen before it.
                    Navigation(navigator = navigator, startDestination = Screen.Lock(resumable = false))
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        if (lockManager.shouldLockOnResume(SystemClock.elapsedRealtime())) {
            lockManager.onLocked()
            navigator.navigate(Screen.Lock(resumable = true)) { launchSingleTop = true }
        }
    }

    override fun onStop() {
        super.onStop()

        lockManager.onBackgrounded(SystemClock.elapsedRealtime())
    }
}
