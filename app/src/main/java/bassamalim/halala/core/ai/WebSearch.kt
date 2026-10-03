package bassamalim.halala.core.ai

import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.data.repositories.IdentifiedAs
import bassamalim.halala.core.data.repositories.PreferencesRepository
import bassamalim.halala.core.data.repositories.SmsRepository
import bassamalim.halala.core.di.IoDispatcher
import bassamalim.halala.core.domain.Identification
import bassamalim.halala.core.enums.BusinessType
import java.time.Clock
import java.time.YearMonth
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.URL
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection

/** One page a web search found. */
data class WebResult(val title: String, val url: String, val content: String)

/**
 * What Halala asks Tavily and reads back, as pure functions. A query is a merchant's name and
 * "Saudi Arabia" (in Arabic for an Arabic name), nothing else.
 */
object TavilyProtocol {

    const val ENDPOINT = "https://api.tavily.com/search"

    /** A basic search costs one credit; five results are plenty to tell what a shop is. */
    private const val MAX_RESULTS = 5

    fun query(name: String): String =
        if (name.any { it in '؀'..'ۿ' }) "$name السعودية" else "$name Saudi Arabia"

    fun request(query: String): String = buildJsonObject {
        put("query", query)
        put("search_depth", "basic")
        put("max_results", MAX_RESULTS)
    }.toString()

    fun parse(body: String): List<WebResult> = json.decodeFromString<Response>(body).results
        .filter { it.url.startsWith("https://") || it.url.startsWith("http://") }
        .map { WebResult(it.title, it.url, it.content) }

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Response(val results: List<Result> = emptyList())

    @Serializable
    private data class Result(val title: String = "", val url: String = "", val content: String = "")
}

/**
 * The spec's web search for cryptic names. A merchant the AI identified at under [SURE]% sure,
 * with at least [FLOOR_MINOR] of spending (small one-offs are never searched), is looked up once,
 * the most money first: its name goes to Tavily, and the name with what Tavily found goes to Groq,
 * which answers again and names the page its answer rests on. A surer answer replaces the first
 * and shows as "Found online". No more than [MONTHLY_CAP] searches a month (the free plan has
 * 1,000 credits); the rest wait for next month.
 */
class WebLookup @Inject constructor(
    private val classification: ClassificationRepository,
    private val accounts: AccountsRepository,
    private val sms: SmsRepository,
    private val keys: ApiKeys,
    private val search: TavilySearch,
    private val groq: GroqHttp,
    private val preferences: PreferencesRepository,
    private val clock: Clock
) {

    /** Never throws: a search that fails stops this run, and the next run carries on. */
    suspend fun run() {
        if (!keys.hasTavily() || !keys.hasGroq()) return
        val last4s = ownLast4s(accounts, sms)
        val month = YearMonth.now(clock)
        var left = MONTHLY_CAP - preferences.webSearches(month)
        try {
            for (merchant in classification.toSearch(SURE, FLOOR_MINOR).take(PER_RUN)) {
                if (left <= 0) return
                // Already checked before the AI saw it; checked again, since this sends it elsewhere.
                if (!Identification.sendable(merchant.descriptor, last4s)) {
                    classification.recordSearch(merchant.merchantId, null, null, null)
                    continue
                }
                val results = search.search(TavilyProtocol.query(merchant.descriptor))
                preferences.countWebSearch(month)
                left--
                val (answer, source) = if (results.isEmpty()) null to null
                else runCatching { GroqProtocol.parseWithSource(groq.post(GroqProtocol.requestWithResults(merchant.descriptor, results)), results.size) }
                    .getOrElse { if (it is IdentifyFailure) throw it else null to null }
                val page = source?.let(results::get)
                classification.recordSearch(merchant.merchantId, answer, page?.url, page?.title)
            }
        } catch (_: IdentifyFailure) {
            // Offline, out of credits, or a key refused: tried again on the next run.
        }
    }

    /**
     * Looks [merchantId] up now, because you asked on its page: its name (only if it could be
     * sent) goes to Tavily while this month's searches last, and the name with what was found
     * goes to Groq; with no search to be had, Groq is asked from the name alone. Nothing changes:
     * what it found is offered, and stands only once you [accept] it.
     */
    suspend fun lookUpNow(merchantId: Long): LookupResult {
        if (!keys.hasGroq()) return LookupResult(LookupOutcome.UNAVAILABLE)
        val descriptor = classification.descriptorOf(merchantId) ?: return LookupResult(LookupOutcome.NOTHING_NEW)
        if (!Identification.sendable(descriptor, ownLast4s(accounts, sms))) return LookupResult(LookupOutcome.WITHHELD)
        val month = YearMonth.now(clock)
        return try {
            val canSearch = keys.hasTavily() && preferences.webSearches(month) < MONTHLY_CAP
            val results = if (!canSearch) emptyList()
            else search.search(TavilyProtocol.query(descriptor)).also {
                preferences.countWebSearch(month)
                classification.markSearched(merchantId)
            }
            val (answer, page) = if (results.isEmpty()) {
                val body = groq.post(GroqProtocol.request(listOf(descriptor)))
                runCatching { GroqProtocol.parse(body, 1).single() }.getOrNull() to null
            } else {
                val body = groq.post(GroqProtocol.requestWithResults(descriptor, results))
                runCatching { GroqProtocol.parseWithSource(body, results.size) }.getOrNull()
                    ?.let { (answer, source) -> answer to source?.let(results::getOrNull) }
                    ?: (null to null)
            }
            if (answer == null || answer.type == BusinessType.UNKNOWN) LookupResult(LookupOutcome.NOTHING_NEW)
            else LookupResult(LookupOutcome.FOUND, LookupFound(answer, page?.url, page?.title))
        } catch (failure: IdentifyFailure) {
            LookupResult(
                when (failure.problem) {
                    IdentifyProblem.UNREACHABLE -> LookupOutcome.OFFLINE
                    IdentifyProblem.LIMITED -> LookupOutcome.LIMITED
                    else -> LookupOutcome.FAILED
                }
            )
        }
    }

    /** "Use this": what [lookUpNow] found becomes what [merchantId] is, as one change you can undo. */
    suspend fun accept(merchantId: Long, found: LookupFound) =
        classification.acceptLookup(merchantId, found.answer, found.url, found.title)

    companion object {
        const val SURE = 80
        const val FLOOR_MINOR = 10_000L
        const val MONTHLY_CAP = 800
        private const val PER_RUN = 20
    }
}

/** What looking a merchant up when you asked came to. */
enum class LookupOutcome { FOUND, NOTHING_NEW, WITHHELD, OFFLINE, LIMITED, FAILED, UNAVAILABLE }

/** What a lookup found, to offer: the AI's [answer], and the page it rests on when it searched. */
data class LookupFound(val answer: IdentifiedAs, val url: String?, val title: String?)

/** How a lookup went, and, when [LookupOutcome.FOUND], what it found. */
data class LookupResult(val outcome: LookupOutcome, val found: LookupFound? = null)

/** Tavily, over HTTPS: [search] sends one query and returns what it found. */
class TavilySearch @Inject constructor(
    private val keys: ApiKeys,
    @param:IoDispatcher private val io: CoroutineDispatcher
) {

    @Throws(IdentifyFailure::class)
    suspend fun search(query: String): List<WebResult> = withContext(io) {
        val key = keys.tavily()?.takeIf { it.isNotBlank() } ?: throw IdentifyFailure(IdentifyProblem.KEY)
        val connection = URL(TavilyProtocol.ENDPOINT).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT
            connection.readTimeout = TIMEOUT
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(TavilyProtocol.request(query).encodeToByteArray()) }

            when (val code = connection.responseCode) {
                401, 403 -> throw IdentifyFailure(IdentifyProblem.KEY)
                // Out of credits (432, 433) or too fast (429): tried again another day.
                429, 432, 433 -> throw IdentifyFailure(IdentifyProblem.LIMITED)
                in 500..599 -> throw IdentifyFailure(IdentifyProblem.UNREACHABLE)
                !in 200..299 -> throw IdentifyFailure(IdentifyProblem.REJECTED, IOException("HTTP $code"))
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            runCatching { TavilyProtocol.parse(body) }.getOrElse { throw IdentifyFailure(IdentifyProblem.REJECTED, it) }
        } catch (e: IOException) {
            throw IdentifyFailure(IdentifyProblem.UNREACHABLE, e)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT = 20_000
    }
}
