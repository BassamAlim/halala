package bassamalim.halala.core.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * Hands every arriving bank SMS to [SmsWorker]. A long SMS comes in parts, joined here. Runs
 * while the phone is locked: the database key isn't bound to the lock (see DatabaseKey).
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val receivedAt = System.currentTimeMillis()

        Telephony.Sms.Intents.getMessagesFromIntent(intent)
            .groupBy { it.displayOriginatingAddress.orEmpty() }
            .filterKeys { SmsParser.bankFor(it) != null }
            .forEach { (sender, parts) ->
                SmsWorker.enqueue(context, sender, parts.joinToString("") { it.displayMessageBody.orEmpty() }, receivedAt)
            }
    }
}
