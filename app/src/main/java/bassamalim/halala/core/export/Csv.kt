package bassamalim.halala.core.export

/**
 * RFC 4180 CSV, written for spreadsheets: CRLF line ends, fields quoted when they hold a comma,
 * quote or line break, quotes doubled.
 *
 * Text that came from outside (a merchant name, later an SMS) could start with `=`, `+`, `-` or
 * `@` and be run as a formula when the file is opened. [text] defuses that with a leading
 * apostrophe; [number] leaves real numbers alone, so a debit still reads as −214.50.
 */
object Csv {

    fun row(fields: List<String>): String = fields.joinToString(",") { quote(it) } + "\r\n"

    fun table(header: List<String>, rows: List<List<String>>): String = buildString {
        append(row(header))
        rows.forEach { append(row(it)) }
    }

    /** A free-text cell, safe from formula injection. */
    fun text(value: String?): String {
        val text = value.orEmpty()
        return if (text.isNotEmpty() && text[0] in FORMULA_STARTS) "'$text" else text
    }

    /** A cell that is meant to be a number, e.g. "-214.50". */
    fun number(value: String): String = value

    private fun quote(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
            "\"" + field.replace("\"", "\"\"") + "\""
        else field

    private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r')
}
