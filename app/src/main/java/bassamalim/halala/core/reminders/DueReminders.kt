package bassamalim.halala.core.reminders

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import bassamalim.halala.R
import bassamalim.halala.core.data.repositories.LoansRepository
import bassamalim.halala.core.data.repositories.PeopleRepository
import bassamalim.halala.core.data.repositories.RecurringRepository
import bassamalim.halala.core.domain.LoanState
import bassamalim.halala.core.domain.SeriesState
import bassamalim.halala.core.enums.LoanDirection
import bassamalim.halala.core.enums.SeriesStatus
import bassamalim.halala.core.utils.shortDateLabel
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** One thing due that you asked to hear about. Never an amount: it shows on the lock screen. */
sealed interface DueNotice {
    /** A stable number per thing, so a notice replaces its own and no other. */
    val key: Int

    /** [name] is due on [due] (its lead time, which you chose, from today). */
    data class Bill(override val key: Int, val name: String, val due: LocalDate) : DueNotice

    /** You asked to be reminded to cancel [name] before it renews on [renews]. */
    data class Cancel(override val key: Int, val name: String, val renews: LocalDate) : DueNotice

    /** A loan with [person] is due today: owed to you when [lent]. */
    data class Loan(override val key: Int, val person: String, val lent: Boolean) : DueNotice
}

/**
 * Bill, renewal and loan reminders: once a day, each thing you asked to hear about whose day it
 * is gets one notification. Quiet otherwise. Bills and subscriptions remind you their lead time
 * before they are due; a cancel reminder comes three days before a renewal, or its lead time if
 * that is longer; a loan, on its due day.
 */
class DueReminders @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock
) {

    /** Keeps the daily check scheduled, at nine in the morning. Safe to call on every start. */
    // ponytail: periodic work runs when the system lets it; a day Doze skips is a reminder missed,
    // since each notice is for one day only.
    fun ensureScheduled() {
        val now = ZonedDateTime.now(clock)
        var next = now.with(CHECK_AT)
        if (!next.isAfter(now)) next = next.plusDays(1)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DueReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
                .build()
        )
    }

    companion object {
        private const val WORK = "due-reminders"
        private const val CHANNEL = "due"
        private const val CANCEL_LEAD_DAYS = 3
        private val CHECK_AT: LocalTime = LocalTime.of(9, 0)

        /** What is due on [today] that you asked to hear about. */
        fun dueOn(today: LocalDate, series: List<SeriesState>, loans: List<LoanState>, names: Map<Long, String>): List<DueNotice> {
            val active = series.filter { it.series.status == SeriesStatus.ACTIVE }
            val bills = active.mapNotNull { state ->
                val due = state.nextDue ?: return@mapNotNull null
                val lead = state.series.reminderDays ?: return@mapNotNull null
                if (due.minusDays(lead.toLong()) == today) DueNotice.Bill(BILL_KEYS + state.series.id.toInt(), state.series.name, due)
                else null
            }
            val cancels = active.mapNotNull { state ->
                val due = state.nextDue ?: return@mapNotNull null
                if (!state.series.cancelReminder) return@mapNotNull null
                val lead = maxOf(CANCEL_LEAD_DAYS, state.series.reminderDays ?: 0)
                if (due.minusDays(lead.toLong()) == today) DueNotice.Cancel(CANCEL_KEYS + state.series.id.toInt(), state.series.name, due)
                else null
            }
            val loansDue = loans.filter { it.isOpen && it.loan.dueOn == today }.map {
                DueNotice.Loan(LOAN_KEYS + it.loan.id.toInt(), names[it.loan.personId].orEmpty(), it.loan.direction == LoanDirection.LENT)
            }
            return bills + cancels + loansDue
        }

        // Notification ids, kept apart from the review reminder's (1) and from each other.
        private const val BILL_KEYS = 100_000
        private const val CANCEL_KEYS = 200_000
        private const val LOAN_KEYS = 300_000

        @SuppressLint("MissingPermission") // areNotificationsEnabled covers POST_NOTIFICATIONS.
        fun notify(context: Context, notices: List<DueNotice>, today: LocalDate) {
            val manager = NotificationManagerCompat.from(context)
            if (notices.isEmpty() || !manager.areNotificationsEnabled()) return

            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.due_channel))
                    .build()
            )
            // Opens the app on its lock, then Home.
            val open = PendingIntent.getActivity(
                context, 0, context.packageManager.getLaunchIntentForPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE
            )

            for (notice in notices) {
                val (title, text) = when (notice) {
                    is DueNotice.Bill -> context.getString(R.string.due_bill_title, notice.name) to
                            context.getString(R.string.due_bill_text, shortDateLabel(notice.due, today))
                    is DueNotice.Cancel -> context.getString(R.string.due_cancel_title, notice.name) to
                            context.getString(R.string.due_cancel_text, shortDateLabel(notice.renews, today))
                    is DueNotice.Loan -> context.getString(
                        if (notice.lent) R.string.due_loan_lent_title else R.string.due_loan_borrowed_title, notice.person
                    ) to context.getString(R.string.due_loan_text)
                }
                manager.notify(
                    notice.key,
                    NotificationCompat.Builder(context, CHANNEL)
                        .setSmallIcon(R.drawable.ic_halala_glyph)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setContentIntent(open)
                        .setAutoCancel(true)
                        .build()
                )
            }
        }
    }
}

/** Looks at what is due today and notifies about what you asked to hear of. */
@HiltWorker
class DueReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val recurring: RecurringRepository,
    private val loans: LoansRepository,
    private val people: PeopleRepository,
    private val clock: Clock
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val today = LocalDate.now(clock)
        val names = people.getPeople().associate { it.id to it.name }
        val notices = DueReminders.dueOn(today, recurring.observeStates().first(), loans.observeStates().first(), names)
        DueReminders.notify(applicationContext, notices, today)
        return Result.success()
    }
}
