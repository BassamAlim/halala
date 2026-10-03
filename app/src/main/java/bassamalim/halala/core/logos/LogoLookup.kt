package bassamalim.halala.core.logos

import android.graphics.BitmapFactory
import bassamalim.halala.core.ai.ApiKeys
import bassamalim.halala.core.ai.GroqHttp
import bassamalim.halala.core.ai.GroqProtocol
import bassamalim.halala.core.ai.IdentifyFailure
import bassamalim.halala.core.ai.ownLast4s
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.LogosRepository
import bassamalim.halala.core.data.repositories.SmsRepository
import bassamalim.halala.core.di.IoDispatcher
import bassamalim.halala.core.domain.Identification
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection

/**
 * Merchants' logos, which you chose to have fetched online. First each identified merchant's own
 * website is asked of Groq, once, in batches of names as the bank wrote them (the same names, and
 * the same checks, as identifying them). Then each website's icon is fetched once from Google's
 * icon service, which so learns the domains of the places you shop; an icon that is missing or
 * too small to show well is remembered as none, and the initial stays.
 */
class LogoLookup @Inject constructor(
    private val logos: LogosRepository,
    private val accounts: AccountsRepository,
    private val sms: SmsRepository,
    private val keys: ApiKeys,
    private val groq: GroqHttp,
    @param:IoDispatcher private val io: CoroutineDispatcher
) {

    /** Never throws: what fails now is tried on the next run. */
    suspend fun run() {
        try {
            if (keys.hasGroq()) askWebsites()
        } catch (_: IdentifyFailure) {
            // Offline or limited: asked next time.
        }
        fetchLogos()
    }

    private suspend fun askWebsites() {
        val last4s = ownLast4s(accounts, sms)
        repeat(MAX_BATCHES) {
            val batch = logos.websitesToAsk().take(BATCH)
            if (batch.isEmpty()) return
            val (sendable, kept) = batch.partition { Identification.sendable(it.descriptor, last4s) }
            logos.recordWebsites(kept.associate { it.merchantId to null })
            if (sendable.isEmpty()) return@repeat
            val body = groq.post(GroqProtocol.websitesRequest(sendable.map { it.descriptor }))
            // An answer that can't be read counts as none, so the batch isn't asked forever.
            val answers = runCatching { GroqProtocol.parseWebsites(body, sendable.size) }.getOrElse { List(sendable.size) { null } }
            logos.recordWebsites(sendable.zip(answers).associate { (merchant, website) -> merchant.merchantId to website })
        }
    }

    private suspend fun fetchLogos() = withContext(io) {
        for (site in logos.toFetch().take(PER_RUN)) {
            val image = try {
                fetch(site.website)
            } catch (_: IOException) {
                return@withContext // Offline: the rest wait for the next run.
            }
            logos.putLogo(site.merchantId, image)
        }
    }

    /** The icon for [domain] as a PNG, or null when there is none worth showing. */
    private fun fetch(domain: String): ByteArray? {
        val url = URL(ICON_URL + URLEncoder.encode(domain, "UTF-8"))
        val connection = url.openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = TIMEOUT
            connection.readTimeout = TIMEOUT
            // The service answers 404 with a generic globe when it knows no icon.
            if (connection.responseCode != 200) return null
            val bytes = connection.inputStream.use { it.readNBytes(MAX_BYTES + 1) }
            if (bytes.size > MAX_BYTES) return null
            return bytes.takeIf { isUsable(it) }
        } finally {
            connection.disconnect()
        }
    }

    private fun isUsable(bytes: ByteArray): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return bounds.outWidth >= MIN_SIZE && bounds.outHeight >= MIN_SIZE
    }

    private companion object {
        const val ICON_URL = "https://www.google.com/s2/favicons?sz=128&domain="
        const val BATCH = 40
        const val MAX_BATCHES = 10
        const val PER_RUN = 100
        const val TIMEOUT = 15_000
        /** Smaller than this looks blurry in a 40dp avatar. */
        const val MIN_SIZE = 48
        const val MAX_BYTES = 200_000
    }
}
