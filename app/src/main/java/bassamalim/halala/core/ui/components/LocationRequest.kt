package bassamalim.halala.core.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import bassamalim.halala.BuildConfig

/**
 * Asks for location the way Android allows: while in use first, then "all the time" (which
 * Android 11 and later only grant from their own settings page). When Android won't ask again,
 * opens Halala's settings instead. [onDone] runs after, to look again.
 */
@Composable
fun rememberLocationRequest(onDone: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onDone() }
    val foreground = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result.values.any { it }
        when {
            granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            !granted && context.findActivity()?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION)
            } == true -> {
                openAppSettings(context)
                onDone()
            }
            else -> onDone()
        }
    }
    return {
        val asked = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        // Android 10 asks for "all the time" in the same dialog.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) asked += Manifest.permission.ACCESS_BACKGROUND_LOCATION
        foreground.launch(asked.toTypedArray())
    }
}

/** Halala's page in Android's settings, where "Allow all the time" is chosen. */
fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", BuildConfig.APPLICATION_ID, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/** Android's location switch. */
fun openLocationSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
