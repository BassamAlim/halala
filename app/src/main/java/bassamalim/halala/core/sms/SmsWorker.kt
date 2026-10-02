package bassamalim.halala.core.sms

import android.content.Context
import android.provider.Telephony
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import bassamalim.halala.core.ai.AiScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Feeds bank SMS to [SmsIngest]: one that just arrived, or (back-import) every bank SMS still in
 * the phone's inbox. All runs share one unique chain, so the pipeline never runs twice at once
 * and a message is never recorded twice.
 */
@HiltWorker
class SmsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val ingest: SmsIngest,
    private val ai: AiScheduler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sender = inputData.getString(KEY_SENDER)
        val body = inputData.getString(KEY_BODY)
        if (sender != null && body != null)
            ingest.store(sender, body, Instant.ofEpochMilli(inputData.getLong(KEY_RECEIVED_AT, 0)))
        if (inputData.getBoolean(KEY_IMPORT_INBOX, false)) importInbox()

        ingest.processPending(retry = inputData.getBoolean(KEY_RETRY, false))
        // New merchants the rules and the bundled list couldn't place go to the AI, when it is on.
        ai.request()
        return Result.success()
    }

    /** Every bank SMS in the inbox. Stored ones are skipped by their hash, so this can run again. */
    private suspend fun importInbox() {
        val senders = BankFormats.ALL.flatMap { it.senders }
        applicationContext.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.ADDRESS} IN (${senders.joinToString { "?" }})",
            senders.toTypedArray(),
            "${Telephony.Sms.DATE} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val body = cursor.getString(1) ?: continue
                ingest.store(cursor.getString(0), body, Instant.ofEpochMilli(cursor.getLong(2)))
            }
        }
    }

    companion object {
        private const val WORK_NAME = "sms"
        private const val KEY_SENDER = "sender"
        private const val KEY_BODY = "body"
        private const val KEY_RECEIVED_AT = "receivedAt"
        private const val KEY_IMPORT_INBOX = "importInbox"
        private const val KEY_RETRY = "retry"

        /** One SMS that just arrived. */
        fun enqueue(context: Context, sender: String, body: String, receivedAt: Long) = enqueue(
            context,
            workDataOf(KEY_SENDER to sender, KEY_BODY to body, KEY_RECEIVED_AT to receivedAt)
        )

        /** The back-import: every bank SMS in the inbox, then the pipeline. */
        fun enqueueImport(context: Context) = enqueue(context, workDataOf(KEY_IMPORT_INBOX to true))

        /** Tries again what no account matched, after accounts were named. */
        fun enqueueRetry(context: Context) = enqueue(context, workDataOf(KEY_RETRY to true))

        /** Whether any run is queued or under way. */
        fun running(context: Context): Flow<Boolean> =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WORK_NAME)
                .map { infos -> infos.any { !it.state.isFinished } }

        private fun enqueue(context: Context, data: androidx.work.Data) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<SmsWorker>().setInputData(data).build()
            )
        }
    }
}
