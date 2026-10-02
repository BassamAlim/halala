package bassamalim.halala.features.assistant

import android.database.SQLException
import bassamalim.halala.core.ai.FailedQuery
import bassamalim.halala.core.ai.IdentifyFailure
import bassamalim.halala.core.ai.IdentifyProblem
import bassamalim.halala.core.ai.QuestionReader
import bassamalim.halala.core.data.repositories.LedgerQueryRepository
import bassamalim.halala.core.domain.Asking
import bassamalim.halala.core.models.QueryResult
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Why a question got no answer. */
enum class AskProblem { NO_KEY, OFFLINE, LIMITED, UNREADABLE, UNSUPPORTED }

/** What a question found, before it is worded. [sql] is the query as the AI wrote it. */
sealed interface Found {
    data class Rows(val sql: String, val result: QueryResult) : Found
    data class Problem(val problem: AskProblem) : Found
}

/**
 * The assistant: the AI reads the question into one SELECT ([QuestionReader]); it runs here on
 * the phone, read-only, and nothing it finds is sent back. A query SQLite refuses gets one more
 * try, with what SQLite said (the query's own words, never a row).
 */
class AssistantDomain @Inject constructor(
    private val reader: QuestionReader,
    private val queries: LedgerQueryRepository,
    private val clock: Clock
) {

    suspend fun answer(question: String): Found {
        val today = LocalDate.now(clock)
        var failed: FailedQuery? = null
        try {
            repeat(TRIES) {
                val sql = reader.read(question, today, failed) ?: return Found.Problem(AskProblem.UNSUPPORTED)
                val query = Asking.wrap(sql)
                val error = if (query == null) NOT_A_SELECT else try {
                    return Found.Rows(sql, queries.run(query, Asking.MAX_ROWS))
                } catch (e: SQLException) {
                    // Android adds the whole statement after the reason; the reason is enough.
                    e.message.orEmpty().substringBefore(", while compiling")
                }
                failed = FailedQuery(sql, error)
            }
        } catch (failure: IdentifyFailure) {
            return Found.Problem(
                when (failure.problem) {
                    IdentifyProblem.KEY -> AskProblem.NO_KEY
                    IdentifyProblem.UNREACHABLE -> AskProblem.OFFLINE
                    IdentifyProblem.LIMITED -> AskProblem.LIMITED
                    IdentifyProblem.REJECTED -> AskProblem.UNREADABLE
                }
            )
        }
        return Found.Problem(AskProblem.UNREADABLE)
    }

    private companion object {
        const val TRIES = 2
        const val NOT_A_SELECT = "Only one SELECT over tx is allowed."
    }
}
