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
    val rules: List<ExportRule> = emptyList(),
    /** Since schema 3. */
    val merchants: List<ExportMerchant> = emptyList()
) {
    companion object {
        const val SCHEMA_VERSION = 4
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
    val ruleUid: String? = null,
    /** Since schema 3: the merchant its title names, when it names one. */
    val merchantUid: String? = null
)

@Serializable
data class ExportCategory(
    val uid: String,
    val name: String,
    val expenseType: String?,
    /** Since schema 4: the business types it takes (`BusinessType` names). */
    val businessTypes: List<String> = emptyList()
)

@Serializable
data class ExportRule(
    val uid: String,
    /** The merchant it matches, as written when the rule was made. */
    val merchant: String?,
    /** Since schema 3: the merchant it was taught, for a learned rule. */
    val merchantUid: String? = null,
    val categoryUid: String?,
    val expenseType: String?,
    val source: String,
    val enabled: Boolean,
    val createdAt: String
)

/**
 * A merchant and every way its bank writes it. Since schema 4, what the business is
 * (`BusinessType` name), who said so (`IdentifiedBy`: LIST, AI, YOU or WITHHELD) and, from the
 * AI, how sure it was (0–100); all null while it is unidentified.
 */
@Serializable
data class ExportMerchant(
    val uid: String,
    val name: String,
    val aliases: List<ExportAlias>,
    val businessType: String? = null,
    val identifiedBy: String? = null,
    val confidence: Int? = null
)

/** One spelling: its key (lower case, letters only), as first written, and how it joined. */
@Serializable
data class ExportAlias(val key: String, val descriptor: String, val matchedBy: String)

@Serializable
data class ExportInternalTransfer(
    val uid: String,
    val outTransactionUid: String,
    val inTransactionUid: String,
    val matchConfidence: Double
)
