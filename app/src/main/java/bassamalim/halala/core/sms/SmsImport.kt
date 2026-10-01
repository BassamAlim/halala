package bassamalim.halala.core.sms

import android.content.Context
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

    /** Tries again the messages no account matched. */
    fun retry() = SmsWorker.enqueueRetry(context)
}
