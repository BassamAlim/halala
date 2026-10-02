package bassamalim.halala.core.export

import kotlinx.serialization.Serializable

/**
 * The JSON export: everything in the ledger, unencrypted and readable, for your own scripts or a
 * move to another tool. Rows are keyed by their stable `uid`s, so a later re-import can merge
 * rather than duplicate. Amounts are integer minor units next to their ISO currency, exactly as
 * stored: `amountMinor: 21450, currency: "SAR"` is 214.50 SAR.
 *
 * Bump [SCHEMA_VERSION] with any change to this shape; importers migrate older files forward.
 */
@Serializable
data class ExportFile(
    val schemaVersion: Int = SCHEMA_VERSION,
    val app: String = "Halala",
    val appVersion: String,
    /** ISO-8601 instant. */
    val exportedAt: String,
    val institutions: List<ExportInstitution>,
    val accounts: List<ExportAccount>,
    val transactions: List<ExportTransaction>,
    val internalTransfers: List<ExportInternalTransfer>,
    /** Since schema 2. */
    val categories: List<ExportCategory> = emptyList(),
    val rules: List<ExportRule> = emptyList()
) {
    companion object {
        const val SCHEMA_VERSION = 2
    }
}

@Serializable
data class ExportInstitution(val name: String, val senderIds: List<String>)

@Serializable
data class ExportAccount(
    val uid: String,
    val institution: String?,
    val nickname: String,
    val type: String,
    val last4: String?,
    val ibanSuffix: String?,
    val currency: String,
    val openingBalanceMinor: Long,
    val archived: Boolean,
    val createdAt: String
)

@Serializable
data class ExportTransaction(
    val uid: String,
    val accountUid: String,
    val direction: String,
    val amountMinor: Long,
    val currency: String,
    /** ISO-8601 instant. */
    val occurredAt: String,
    val kind: String,
    val title: String,
    val note: String,
    val source: String,
    val createdAt: String,
    /** Since schema 2: what it is filed under, and the rule that filed it (null when you did). */
    val categoryUid: String? = null,
    val expenseType: String? = null,
    val ruleUid: String? = null
)

@Serializable
data class ExportCategory(val uid: String, val name: String, val expenseType: String?)

@Serializable
data class ExportRule(
    val uid: String,
    /** The merchant it matches, as written when the rule was made. */
    val merchant: String?,
    val categoryUid: String?,
    val expenseType: String?,
    val source: String,
    val enabled: Boolean,
    val createdAt: String
)

@Serializable
data class ExportInternalTransfer(
    val uid: String,
    val outTransactionUid: String,
    val inTransactionUid: String,
    val matchConfidence: Double
)
