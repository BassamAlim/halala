package bassamalim.halala.core.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Starts the SMS work and says whether it is running, so nothing above it needs a Context. */
@Singleton
class SmsImport @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    val running: Flow<Boolean> get() = SmsWorker.running(context)

    /** Reads every bank SMS in the inbox. Needs READ_SMS; messages already kept are skipped. */
    fun start() = SmsWorker.enqueueImport(context)

    /**
     * Reads the inbox again when Halala may (READ_SMS), so whatever it missed comes in: SMS that
     * arrived while it had no permission, or that Android never handed it. Kept ones are skipped.
     */
    // ponytail: scans every bank SMS each opening; read only since the newest kept one if it ever shows.
    fun catchUp() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) start()
    }

    /** Tries again the messages no account matched. */
    fun retry() = SmsWorker.enqueueRetry(context)
}
