package bassamalim.halala.features.assistant

/** One cell of an answer, already formatted. [money] cells are amounts, [number] cells any figure. */
data class Cell(val text: String, val number: Boolean = false, val money: Boolean = false, val currency: String? = null)

/** An answer, already formatted. */
sealed interface Reply {
    data object Thinking : Reply
    data class Problem(val problem: AskProblem) : Reply
    /** [sql] is the query that found it, shown as how it was worked out. [more] when rows were left out. */
    data class Table(val headings: List<String>, val rows: List<List<Cell>>, val sql: String, val more: Boolean) : Reply
}

/** The question asked and its answer; only the latest is kept. */
data class Exchange(val question: String, val reply: Reply)

data class AssistantUiState(
    val exchange: Exchange? = null,
    val draft: String = "",
    val busy: Boolean = false
)
