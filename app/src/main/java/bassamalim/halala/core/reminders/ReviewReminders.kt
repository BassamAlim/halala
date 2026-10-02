package bassamalim.halala.core.reminders

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import bassamalim.halala.R
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.domain.Rules
import bassamalim.halala.core.models.ReminderMode
import bassamalim.halala.core.models.ReviewSchedule
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * The review reminder: one notification, at the time you chose, saying how many merchants wait
 * in the inbox, and nothing at all when none do. The app never notifies per transaction.
 */
class ReviewReminders @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock
) {

    /** Sets the reminder to [schedule], replacing whatever was set; OFF clears it. */
    // ponytail: WorkManager runs when the system lets it, so a reminder can be minutes late in
    // Doze; an exact alarm would be on time, at the cost of its own permission.
    fun schedule(schedule: ReviewSchedule) {
        val workManager = WorkManager.getInstance(context)
        val next = nextAt(ZonedDateTime.now(clock), schedule)
        if (next == null) {
            workManager.cancelUniqueWork(WORK)
            return
        }

        workManager.enqueueUniquePeriodicWork(
            WORK,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            PeriodicWorkRequestBuilder<ReviewReminderWorker>(
                if (schedule.mode == ReminderMode.DAILY) 1L else 7L,
                TimeUnit.DAYS
            )
                .setInitialDelay(Duration.between(ZonedDateTime.now(clock), next).toMillis(), TimeUnit.MILLISECONDS)
                .build()
        )
    }

    companion object {
        private const val WORK = "review-reminder"
        private const val SNOOZE_WORK = "review-snooze"
        private const val CHANNEL = "review"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_HOURS = "hours"

        /** The next time the reminder is due, strictly after [now]; null when it is off. */
        fun nextAt(now: ZonedDateTime, schedule: ReviewSchedule): ZonedDateTime? {
            val today = now.with(schedule.time)
            return when (schedule.mode) {
                ReminderMode.OFF -> null
                ReminderMode.DAILY -> if (today.isAfter(now)) today else today.plusDays(1)
                ReminderMode.WEEKLY -> {
                    val thisWeek = today.with(TemporalAdjusters.nextOrSame(schedule.day))
                    if (thisWeek.isAfter(now)) thisWeek else thisWeek.plusWeeks(1)
                }
            }
        }

        /** Says how many wait, with the board's two ways to put it off. Quiet when nothing waits. */
        @SuppressLint("MissingPermission") // areNotificationsEnabled covers POST_NOTIFICATIONS.
        fun notify(context: Context, count: Int) {
            val manager = NotificationManagerCompat.from(context)
            if (count == 0 || !manager.areNotificationsEnabled()) return

            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.reminder_channel))
                    .build()
            )

            // Opens the app on its lock, then Home, where the review pill is one tap away.
            val open = PendingIntent.getActivity(
                context,
                0,
                context.packageManager.getLaunchIntentForPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE
            )

            manager.notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_halala_glyph)
                    // A count of merchants, never an amount: this shows on the lock screen.
                    .setContentTitle(context.resources.getQuantityString(R.plurals.review_count, count, count))
                    .setContentText(context.getString(R.string.reminder_text))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .addAction(0, context.getString(R.string.reminder_in_1_hour), snoozeIntent(context, 1))
                    .addAction(0, context.getString(R.string.reminder_tomorrow), snoozeIntent(context, 24))
                    .build()
            )
        }

        private fun snoozeIntent(context: Context, hours: Int) = PendingIntent.getBroadcast(
            context,
            hours,
            Intent(context, SnoozeReceiver::class.java).putExtra(EXTRA_HOURS, hours),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** "In 1 hour" and "Tomorrow": puts the notification away and asks once more after that long. */
    class SnoozeReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            WorkManager.getInstance(context).enqueueUniqueWork(
                SNOOZE_WORK,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ReviewReminderWorker>()
                    .setInitialDelay(intent.getIntExtra(EXTRA_HOURS, 1).toLong(), TimeUnit.HOURS)
                    .build()
            )
        }
    }
}

/** Counts the inbox when the reminder is due and notifies if anything waits. */
@HiltWorker
class ReviewReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val transactions: TransactionsRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        ReviewReminders.notify(applicationContext, Rules.clusters(transactions.observeAll().first()).size)
        return Result.success()
    }
}
