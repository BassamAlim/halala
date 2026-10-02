package bassamalim.halala.core.prices

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import bassamalim.halala.core.Globals
import bassamalim.halala.core.data.repositories.AssetsRepository
import bassamalim.halala.core.di.IoDispatcher
import bassamalim.halala.core.enums.AssetType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection

/**
 * Market prices for your assets: gold from a public quote, funds from Mubasher's list. Assets
 * linked to a source ([bassamalim.halala.core.data.dataSources.room.entities.Asset.priceSource])
 * take the new price and its day; the rest stay as you typed them. Daily, online only.
 */
@Singleton
class Prices @Inject constructor(
    private val application: Application,
    private val assets: AssetsRepository,
    @param:IoDispatcher private val io: CoroutineDispatcher
) {

    @Volatile private var fundsCache: List<ListedFund>? = null

    suspend fun gold(): GoldPrice? = withContext(io) { runCatching { PriceProtocol.gold(get(PriceProtocol.GOLD_URL)) }.getOrNull() }

    /** Every listed fund, fetched once per run of the app. */
    suspend fun funds(): List<ListedFund>? = fundsCache ?: withContext(io) {
        runCatching { PriceProtocol.funds(get(PriceProtocol.FUNDS_URL)) }.getOrNull()?.takeIf { it.isNotEmpty() }?.also { fundsCache = it }
    }

    /** Updates every linked asset. True when all the sources needed answered. */
    suspend fun refresh(): Boolean {
        val linked = assets.getAll().filter { it.priceSource != null }
        if (linked.isEmpty()) return true
        fundsCache = null
        val gold = if (linked.any { it.priceSource == PriceProtocol.GOLD_SOURCE }) gold() else null
        val funds = if (linked.any { PriceProtocol.fundIdOf(it.priceSource) != null }) funds()?.associateBy { it.id } else null
        var complete = true
        for (asset in linked) {
            val updated = when {
                asset.type == AssetType.GOLD && asset.priceSource == PriceProtocol.GOLD_SOURCE ->
                    gold?.let { asset.copy(unitPrice = it.perGram.toPlainString(), priceDate = it.date) }
                asset.type == AssetType.FUND -> PriceProtocol.fundIdOf(asset.priceSource)?.let { id -> funds?.get(id) }
                    ?.let { asset.copy(unitPrice = it.price.toPlainString(), priceDate = it.date ?: asset.priceDate) }
                else -> asset
            }
            if (updated == null) complete = false else if (updated != asset) assets.save(updated)
        }
        assets.snapshot(Globals.PRIMARY_CURRENCY)
        return complete
    }

    fun ensureScheduled() {
        WorkManager.getInstance(application).enqueueUniquePeriodicWork(
            WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PriceWorker>(1, TimeUnit.DAYS).setConstraints(online).build()
        )
    }

    /** Right away, after linking an asset. */
    fun refreshSoon() {
        WorkManager.getInstance(application).enqueueUniqueWork(
            WORK_NOW,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<PriceWorker>().setConstraints(online).build()
        )
    }

    private val online get() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = TIMEOUT
            connection.readTimeout = TIMEOUT
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val WORK = "prices"
        const val WORK_NOW = "prices-now"
        const val TIMEOUT = 30_000
    }
}

@HiltWorker
class PriceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val prices: Prices
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (prices.refresh() || runAttemptCount >= 2) Result.success() else Result.retry()
}
