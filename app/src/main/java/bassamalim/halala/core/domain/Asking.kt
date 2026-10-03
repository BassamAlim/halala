package bassamalim.halala.core.domain

import bassamalim.halala.core.enums.TransactionKind

/**
 * The assistant's queries, as pure functions. The AI writes one SELECT over [VIEW]'s `tx`, a
 * flat reading of the ledger with its rules already applied (what counts as spending, your
 * share of a split, a merchant however its bank spells it), so a query can't get them wrong.
 */
object Asking {

    /** The most rows an answer shows. */
    const val MAX_ROWS = 50

    private const val LOCAL = "t.occurredAt / 1000, 'unixepoch', 'localtime'"

    private fun kinds(kinds: Iterable<TransactionKind>) = kinds.joinToString { "'${it.name}'" }

    /** `tx`: one row per transaction. Its columns are described to the AI in `AssistantProtocol`. */
    val VIEW = """
        WITH tx AS (
            SELECT t.id AS id,
                date($LOCAL) AS day,
                strftime('%Y-%m', $LOCAL) AS month,
                CASE strftime('%w', $LOCAL) WHEN '0' THEN 'Sunday' WHEN '1' THEN 'Monday' WHEN '2' THEN 'Tuesday'
                    WHEN '3' THEN 'Wednesday' WHEN '4' THEN 'Thursday' WHEN '5' THEN 'Friday' ELSE 'Saturday' END AS weekday,
                CAST(strftime('%H', $LOCAL) AS INTEGER) AS hour,
                t.amountMinor - (SELECT COALESCE(SUM(e.amountMinor), 0) FROM loans l JOIN loan_events e ON e.loanId = l.id
                    WHERE l.splitOf = t.id AND e.type = 'DISBURSEMENT') AS amount_minor,
                t.currency AS currency,
                CASE WHEN x.id IS NOT NULL OR t.kind IN (${kinds(TransactionKind.entries.filter { !it.countsInTotals })}) THEN 'moved'
                    WHEN t.direction = 'DEBIT' THEN 'spent' ELSE 'income' END AS flow,
                t.kind AS kind,
                c.name AS category,
                t.expenseType AS expense_type,
                m.name AS merchant,
                m.businessType AS business_type,
                p.name AS person,
                a.nickname AS account,
                i.name AS bank,
                t.title AS title,
                t.note AS note,
                (SELECT group_concat(g.name, ', ') FROM transaction_tags tt JOIN tags g ON g.id = tt.tagId
                    WHERE tt.transactionId = t.id AND tt.removed = 0) AS tags
            FROM transactions t
            JOIN accounts a ON a.id = t.accountId
            LEFT JOIN institutions i ON i.id = a.institutionId
            LEFT JOIN internal_transfers x ON x.outTransactionId = t.id OR x.inTransactionId = t.id
            LEFT JOIN categories c ON c.id = t.categoryId
            LEFT JOIN merchant_aliases ma ON ma.aliasKey = t.merchantKey AND t.merchantKey != ''
            LEFT JOIN merchants m ON m.id = ma.merchantId
            LEFT JOIN person_aliases pa ON pa.aliasKey = t.merchantKey AND t.merchantKey != ''
                AND x.id IS NULL AND t.kind IN (${kinds(People.KINDS)})
            LEFT JOIN people p ON p.id = pa.personId
        )
    """.trimIndent()

    /**
     * What the AI wrote, over [VIEW]: one SELECT and nothing else, or null. It also runs on a
     * read-only connection; this only turns away what was never a query.
     */
    fun wrap(sql: String): String? {
        val query = sql.trim().trimEnd(';').trim()
        if (';' in query) return null
        return when {
            query.startsWith("select", ignoreCase = true) -> "$VIEW $query"
            // Its own WITH joins ours.
            Regex("^with\\s", RegexOption.IGNORE_CASE).containsMatchIn(query) -> "$VIEW, ${query.drop(4).trim()}"
            else -> null
        }
    }

    /** Whether a column holds money in minor units: the AI names those `…_minor`. */
    fun isMoney(column: String) = column.endsWith(MINOR, ignoreCase = true)

    /** A column's heading: "total_minor" is "Total". */
    fun heading(column: String): String =
        (if (isMoney(column)) column.dropLast(MINOR.length) else column)
            .replace('_', ' ').trim().replaceFirstChar { it.uppercase() }

    private const val MINOR = "_minor"
}
