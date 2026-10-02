package bassamalim.halala.core.ai

import bassamalim.halala.core.enums.BusinessType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

/** The questions the assistant can answer: each is a query Halala runs on the phone. */
enum class AskTool { SPENDING, INCOME, BILLS, OWED, AFFORD, BALANCE, UNSUPPORTED }

/**
 * A question as the AI read it: which query, about what, over which days. [topic] is your own
 * words for what was bought ("coffee"), matched on the phone against your categories and
 * merchants; [businessType] is the AI's reading of it, for when nothing of yours matches.
 */
data class Ask(
    val tool: AskTool,
    val topic: String? = null,
    val businessType: BusinessType? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val amount: BigDecimal? = null,
    val on: LocalDate? = null,
    val monthly: Boolean = false
)

/**
 * The assistant's "tool calling", as pure functions: the AI is told today's date and your
 * question and answers with one query in a strict JSON schema. It is never shown a figure,
 * a category or anything else from the ledger: the phone runs the query and words the answer.
 */
object AssistantProtocol {

    fun request(question: String, today: LocalDate): String = buildJsonObject {
        put("model", GroqProtocol.MODEL)
        put("temperature", 0)
        put("reasoning_effort", "none")
        put("max_completion_tokens", MAX_TOKENS)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "system")
                put("content", INSTRUCTIONS + "\nToday is $today.")
            }
            addJsonObject {
                put("role", "user")
                put("content", question.take(MAX_QUESTION))
            }
        }
        putJsonObject("response_format") {
            put("type", "json_schema")
            putJsonObject("json_schema") {
                put("name", "query")
                put("strict", true)
                put("schema", SCHEMA)
            }
        }
    }.toString()

    /** The query in a completion; anything it couldn't read is left out rather than guessed. */
    fun parse(body: String): Ask {
        val query = json.decodeFromString<Query>(GroqProtocol.contentOf(body))
        fun date(text: String?) = text?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }
        return Ask(
            tool = AskTool.entries.firstOrNull { it.name == query.tool } ?: AskTool.UNSUPPORTED,
            topic = query.topic?.trim()?.takeIf { it.isNotEmpty() },
            businessType = BusinessType.entries.firstOrNull { it.name == query.businessType && it != BusinessType.UNKNOWN },
            from = date(query.from),
            to = date(query.to),
            amount = query.amount?.trim()?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 },
            on = date(query.on),
            monthly = query.monthly == true
        )
    }

    private const val MAX_TOKENS = 512
    private const val MAX_QUESTION = 500

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Query(
        val tool: String,
        val topic: String? = null,
        val businessType: String? = null,
        val from: String? = null,
        val to: String? = null,
        val amount: String? = null,
        val on: String? = null,
        val monthly: Boolean? = null
    )

    private fun nullable(type: String) = buildJsonObject {
        put("type", JsonArray(listOf(JsonPrimitive(type), JsonPrimitive("null"))))
    }

    private val FIELDS = listOf("tool", "topic", "businessType", "from", "to", "amount", "on", "monthly")

    private val SCHEMA: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("tool") {
                put("type", "string")
                putJsonArray("enum") { AskTool.entries.forEach { add(it.name) } }
            }
            put("topic", nullable("string"))
            putJsonObject("businessType") {
                put("type", JsonArray(listOf(JsonPrimitive("string"), JsonPrimitive("null"))))
                putJsonArray("enum") {
                    BusinessType.entries.forEach { add(it.name) }
                    add(JsonPrimitive(null as String?))
                }
            }
            put("from", nullable("string"))
            put("to", nullable("string"))
            put("amount", nullable("string"))
            put("on", nullable("string"))
            put("monthly", nullable("boolean"))
        }
        put("required", JsonArray(FIELDS.map(::JsonPrimitive)))
        put("additionalProperties", false)
    }

    private val INSTRUCTIONS = """
        You turn a question about the user's own money into one query for a finance app. You never see their data; the app answers.
        Fields:
        - tool: SPENDING (how much was spent, on what), INCOME (money that came in), BILLS (the biggest bills), OWED (who owes whom, loans),
          AFFORD (can they afford a purchase), BALANCE (how much they have now), UNSUPPORTED (anything else).
        - topic: what the money was spent on or where, in the user's words, singular ("coffee", "Panda", "groceries"). Null for all spending.
        - businessType: the kind of business topic means, from the list below, or null.
        - from, to: the days asked about, as YYYY-MM-DD. "Since June" is from the 1st of the last June up to null. Null when not said.
        - amount: for AFFORD, the amount as plain digits ("12000"). Otherwise null.
        - on: for AFFORD, the day of the purchase as YYYY-MM-DD ("in Nov" is the 1st of the next November). Null for today.
        - monthly: for AFFORD, true when it would be paid every month.
        Business types:
    """.trimIndent() + "\n" + BusinessType.entries.joinToString("\n") { "- ${it.name}: ${GroqProtocol.MEANINGS.getValue(it)}" }
}

/** Reads a question into a query. */
interface QuestionReader {
    @Throws(IdentifyFailure::class)
    suspend fun read(question: String, today: LocalDate): Ask
}

/** Groq: the question and today's date go out, a query comes back. */
class GroqQuestionReader @Inject constructor(private val groq: GroqHttp) : QuestionReader {
    override suspend fun read(question: String, today: LocalDate): Ask {
        val body = groq.post(AssistantProtocol.request(question, today))
        return runCatching { AssistantProtocol.parse(body) }.getOrElse { throw IdentifyFailure(IdentifyProblem.REJECTED, it) }
    }
}
