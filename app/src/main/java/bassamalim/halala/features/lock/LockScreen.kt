package bassamalim.halala.features.lock

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import bassamalim.halala.R
import bassamalim.halala.core.ui.components.ButtonKind
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaTheme
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/**
 * The app's own locked surface. Android draws the biometric sheet itself, so all this screen
 * owns is the empty state behind it and the Unlock button you get if you dismiss the prompt.
 * Nothing about your money is on screen before you're through it, not even a balance.
 */
@Composable
fun LockScreen(viewModel: LockViewModel = hiltViewModel()) {
    val activity = LocalContext.current.findFragmentActivity()
    val promptTitle = stringResource(R.string.lock_prompt_title)

    val unlock = {
        if (activity == null || !activity.canAuthenticate()) {
            // A phone with no screen lock at all must still open: you can never lock yourself out.
            viewModel.onUnlocked()
        }
        else {
            activity.promptForUnlock(title = promptTitle, onSuccess = viewModel::onUnlocked)
        }
    }

    // Backing out of the lock would be a way past it.
    BackHandler(enabled = true) {}

    LaunchedEffect(Unit) { unlock() }

    LockContent(onUnlockClick = unlock)
}

@Composable
private fun LockContent(onUnlockClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen)
            .padding(bottom = Spacing.section),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.ic_halala_mark),
                contentDescription = null,
                modifier = Modifier.size(Sizes.lockMark)
            )

            Text(
                text = stringResource(R.string.app_name),
                style = HalalaType.ScreenTitle,
                modifier = Modifier.padding(top = Spacing.md)
            )

            Text(text = stringResource(R.string.lock_locked), style = HalalaType.Label, color = HalalaColors.TextMuted)
        }

        HalalaButton(
            text = stringResource(R.string.lock_unlock),
            onClick = onUnlockClick,
            kind = ButtonKind.Primary,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = stringResource(R.string.lock_hint),
            style = HalalaType.Caption,
            color = HalalaColors.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.card)
        )
    }
}

/**
 * Strong (class 3) biometrics with the device credential as fallback. Android 10 can't combine
 * those two, so there it accepts any biometric with the credential instead.
 */
private val AUTHENTICATORS =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

internal fun FragmentActivity.canAuthenticate() =
    BiometricManager.from(this).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

internal fun FragmentActivity.promptForUnlock(title: String, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(
        this,
        ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        }
    )

    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            // Device credential is the fallback, so no negative button is allowed here.
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
    )
}

internal tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

@Preview
@Composable
private fun LockPreview() = HalalaTheme { LockContent(onUnlockClick = {}) }
