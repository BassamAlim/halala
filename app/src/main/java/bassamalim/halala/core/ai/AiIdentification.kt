package bassamalim.halala.core.ai

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.data.repositories.IdentifiedAs
import bassamalim.halala.core.data.repositories.SmsRepository
import bassamalim.halala.core.domain.Identification
import bassamalim.halala.core.enums.BusinessType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Identifies the merchants waiting for it, a batch of names at a time, the busiest first. Only
 * a name ever leaves the phone, and only one that can't hold more than a shop's name
 * ([Identification.sendable]); the rest are kept back for you. Each merchant is asked about once.
 */
class AiIdentification @Inject constructor(
    private val classification: ClassificationRepository,
    private val accounts: AccountsRepository,
    private val sms: SmsRepository,
    private val keys: ApiKeys,
    private val identifier: MerchantIdentifier
) {

    /** It is always on, in a build that has Groq's key: nothing is sent otherwise. */
    fun isOn(): Boolean = keys.hasGroq()

    @Throws(IdentifyFailure::class)
    suspend fun run() {
        if (!isOn()) return
        // The digits that name your accounts and cards: a name holding one is never sent.
        val last4s = ownLast4s(accounts, sms)

        repeat(MAX_BATCHES) {
            val waiting = classification.toIdentify()
            val (sendable, kept) = waiting.partition { Identification.sendable(it.descriptor, last4s) }
            if (kept.isNotEmpty()) classification.withhold(kept.map { it.merchantId })
            if (sendable.isEmpty()) return

            val batch = sendable.take(BATCH)
            val answers = identifier.identify(batch.map { it.descriptor })
            // One it left out is asked about no more: it waits for you, like an unknown one.
            classification.recordIdentifications(
                batch.zip(answers).associate { (merchant, answer) ->
                    merchant.merchantId to (answer ?: IdentifiedAs("", BusinessType.UNKNOWN, 0))
                }
            )
        }
    }

    private companion object {
        /** Names per request: the spec's batches of about 40. */
        const val BATCH = 40

        /** Requests per run, well inside Groq's free limits; a long back-import goes on next time. */
        const val MAX_BATCHES = 20
    }
}

/** The digits that name your accounts and cards, last four each. */
internal suspend fun ownLast4s(accounts: AccountsRepository, sms: SmsRepository): Set<String> =
    (accounts.getAll().mapNotNull { it.last4 } + sms.getRefs().map { it.ref })
        .map { it.filter(Char::isDigit).takeLast(4) }
        .filter { it.length == 4 }
        .toSet()

/** Starts identifying in the background, when the build has a key: after SMS arrive, and as the app opens. */
class AiScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val identification: AiIdentification
) {

    suspend fun request() {
        if (identification.isOn()) IdentifyWorker.enqueue(context)
    }
}

/**
 * One run of [AiIdentification], then [WebLookup] for what it was unsure of, then [PeopleMatching],
 * online only. A problem worth trying again is retried later.
 */
@HiltWorker
class IdentifyWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val identification: AiIdentification,
    private val webLookup: WebLookup,
    private val peopleMatching: PeopleMatching
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        identification.run()
        webLookup.run()
        peopleMatching.run()
        Result.success()
    } catch (failure: IdentifyFailure) {
        if (failure.problem.retry) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK = "identify"

        /** One run at a time: a run asks again for what arrives while it works. */
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<IdentifyWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                    .build()
            )
        }
    }
}
