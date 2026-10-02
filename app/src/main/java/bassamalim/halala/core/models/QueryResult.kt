package bassamalim.halala.core.models

/** What a query found: each cell a `Long`, a `Double`, a `String` or null. [more] when rows were left out. */
data class QueryResult(val columns: List<String>, val rows: List<List<Any?>>, val more: Boolean)
