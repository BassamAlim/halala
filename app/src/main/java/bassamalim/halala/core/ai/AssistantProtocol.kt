package bassamalim.halala.core.ai

import bassamalim.halala.core.Globals
import bassamalim.halala.core.domain.Asking
import bassamalim.halala.core.enums.BusinessType
import bassamalim.halala.core.enums.ExpenseType
import bassamalim.halala.core.enums.TransactionKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import javax.inject.Inject

/** A query that didn't run, and what SQLite said about it, for one more try. */
data class FailedQuery(val sql: String, val error: String)

/**
 * The assistant's text-to-SQL, as pure functions: the AI is told today's date, the columns of
 * `tx` (`Asking.VIEW`) and your question, and answers with one SELECT in a strict JSON schema.
 * It is never shown a figure, a category or anything else from the ledger: the phone runs the
 * query and shows what it finds.
 */
object AssistantProtocol {

    fun request(question: String, today: LocalDate, failed: FailedQuery? = null): String = buildJsonObject {
        put("model", GroqProtocol.MODEL)
        put("temperature", 0)
        put("reasoning_effort", "none")
        put("max_completion_tokens", MAX_TOKENS)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "system")
                put("content", INSTRUCTIONS + "\nToday is $today, a ${today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}.")
            }
            addJsonObject {
                put("role", "user")
                put("content", question.take(MAX_QUESTION))
            }
            if (failed != null) {
                addJsonObject {
                    put("role", "assistant")
                    put("content", buildJsonObject { put("sql", failed.sql) }.toString())
                }
                addJsonObject {
                    put("role", "user")
                    put("content", "SQLite refused that: ${failed.error.take(MAX_ERROR)}\nWrite it again.")
                }
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

    /** The SELECT in a completion, or null when the AI said the question isn't one `tx` answers. */
    fun parse(body: String): String? =
        json.decodeFromString<Query>(GroqProtocol.contentOf(body)).sql?.trim()?.takeIf { it.isNotEmpty() }

    private const val MAX_TOKENS = 768
    private const val MAX_QUESTION = 500
    private const val MAX_ERROR = 300

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Query(val sql: String? = null)

    private val SCHEMA: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("sql") { put("type", JsonArray(listOf(JsonPrimitive("string"), JsonPrimitive("null")))) }
        }
        put("required", JsonArray(listOf(JsonPrimitive("sql"))))
        put("additionalProperties", false)
    }

    private val INSTRUCTIONS = """
        You turn a question about the user's own money into one SQLite SELECT for a finance app. You never see their data; the app runs the query and shows the rows.
        The only table is tx, one row per transaction:
        - day TEXT 'YYYY-MM-DD', month TEXT 'YYYY-MM', weekday TEXT ('Sunday' to 'Saturday'), hour INTEGER 0-23: when it happened, local time.
        - amount_minor INTEGER: the amount in minor units (halalas), always positive.
        - currency TEXT: ISO code.
        - flow TEXT: 'spent' (money out that counts as spending), 'income' (money in that counts), 'moved' (between their own accounts, loans, corrections: neither).
        - kind TEXT: one of ${TransactionKind.entries.joinToString { it.name }}.
        - category TEXT: their own name for what it was spent on; null when not filed.
        - expense_type TEXT: one of ${ExpenseType.entries.joinToString { it.name }}, or null.
        - merchant TEXT: the business; business_type TEXT: what kind of business, from the list below.
        - person TEXT: who a transfer was with. account TEXT, bank TEXT: where the money was.
        - title TEXT: the bank's words. note TEXT. tags TEXT: comma-separated tag names, or null.
        Rules:
        - Answer with one SELECT over tx. No other table, no semicolon. Use subqueries, not WITH.
        - Spending is flow = 'spent'; income is flow = 'income'.
        - Add currency = '${Globals.PRIMARY_CURRENCY}' unless another currency is asked about; then select currency too.
        - Every money column in the result must be named ending in _minor and stay an integer in minor units: never divide by 100. An average is SUM(amount_minor) / COUNT(*) AS average_minor.
        - Name the other columns in short snake_case ("month", "merchant", "purchases").
        - You don't know their category or merchant names. For a topic ("coffee"), match loosely:
          (category LIKE '%coffee%' OR merchant LIKE '%coffee%' OR title LIKE '%coffee%' OR business_type = 'CAFE').
        - When no days are named, use this month. "Since June" is from the 1st of the last June. Compare days as text: day >= '2026-06-01'.
        - For a list of transactions select day, COALESCE(merchant, person, title) AS name, amount_minor, newest first.
        - At most ${Asking.MAX_ROWS} rows: add a LIMIT.
        - sql is null when tx can't answer it (balances, forecasts, who owes whom, anything not about transactions).
        Business types:
    """.trimIndent() + "\n" + BusinessType.entries.joinToString("\n") { "- ${it.name}: ${GroqProtocol.MEANINGS.getValue(it)}" }
}

/** Reads a question into a query; [failed] is the try before, when SQLite refused it. */
interface QuestionReader {
    @Throws(IdentifyFailure::class)
    suspend fun read(question: String, today: LocalDate, failed: FailedQuery? = null): String?
}

/** Groq: the question and today's date go out, a query comes back. */
class GroqQuestionReader @Inject constructor(private val groq: GroqHttp) : QuestionReader {
    override suspend fun read(question: String, today: LocalDate, failed: FailedQuery?): String? {
        val body = groq.post(AssistantProtocol.request(question, today, failed))
        return runCatching { AssistantProtocol.parse(body) }.getOrElse { throw IdentifyFailure(IdentifyProblem.REJECTED, it) }
    }
}
