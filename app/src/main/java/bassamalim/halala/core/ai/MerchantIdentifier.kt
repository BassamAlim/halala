package bassamalim.halala.core.ai

import bassamalim.halala.core.data.repositories.IdentifiedAs
import bassamalim.halala.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URL
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection

/** Says what businesses are, from their names alone. */
interface MerchantIdentifier {

    /** An answer for each of [names], in order; null for one it couldn't answer. */
    @Throws(IdentifyFailure::class)
    suspend fun identify(names: List<String>): List<IdentifiedAs?>
}

/** Why identifying stopped, which decides whether it is tried again by itself. */
enum class IdentifyProblem(val retry: Boolean) {
    /** No key, or Groq refused it: only you can fix that. */
    KEY(retry = false),

    /** Offline, or Groq didn't answer. */
    UNREACHABLE(retry = true),

    /** Groq's free limit for now was reached. */
    LIMITED(retry = true),

    /** Groq answered, but not with something Halala could read. Asked again next time. */
    REJECTED(retry = false)
}

class IdentifyFailure(val problem: IdentifyProblem, cause: Throwable? = null) : Exception(problem.name, cause)

/** Groq, over HTTPS. Sends the names in [GroqProtocol.request] and nothing else. */
class GroqIdentifier @Inject constructor(private val groq: GroqHttp) : MerchantIdentifier {

    override suspend fun identify(names: List<String>): List<IdentifiedAs?> {
        val body = groq.post(GroqProtocol.request(names))
        return runCatching { GroqProtocol.parse(body, names.size) }
            .getOrElse { throw IdentifyFailure(IdentifyProblem.REJECTED, it) }
    }
}

/** One chat completion from Groq: [post] sends a request body and returns the response's. */
class GroqHttp @Inject constructor(
    private val keys: ApiKeys,
    @param:IoDispatcher private val io: CoroutineDispatcher
) {

    @Throws(IdentifyFailure::class)
    suspend fun post(request: String): String = withContext(io) {
        val key = keys.groq()?.takeIf { it.isNotBlank() } ?: throw IdentifyFailure(IdentifyProblem.KEY)
        val connection = URL(GroqProtocol.ENDPOINT).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(request.encodeToByteArray()) }

            when (val code = connection.responseCode) {
                401, 403 -> throw IdentifyFailure(IdentifyProblem.KEY)
                429 -> throw IdentifyFailure(IdentifyProblem.LIMITED)
                in 500..599 -> throw IdentifyFailure(IdentifyProblem.UNREACHABLE)
                !in 200..299 -> throw IdentifyFailure(IdentifyProblem.REJECTED, IOException("HTTP $code"))
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            throw IdentifyFailure(IdentifyProblem.UNREACHABLE, e)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT = 15_000
        const val READ_TIMEOUT = 60_000
    }
}
