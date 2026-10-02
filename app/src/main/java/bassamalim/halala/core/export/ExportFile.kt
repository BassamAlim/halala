package bassamalim.halala.core.export

import kotlinx.serialization.Serializable

/**
 * The JSON export: everything in the ledger, unencrypted and readable, for your own scripts or a
 * move to another tool. Rows are keyed by their stable `uid`s, so a later re-import can merge
 * rather than duplicate. Amounts are integer minor units next to their ISO currency, exactly as
 * stored: `amountMinor: 21450, currency: "SAR"` is 214.50 SAR.
 *
 * Since schema 5 it holds all that a restore needs ([Importer]): the bank messages as they
 * arrived, the digits learned for each account and the balances the banks reported, so a
 * restored phone reads its inbox again without recording anything twice. Only the history of
 * changes (undo) is left out.
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
    val merchants: List<ExportMerchant> = emptyList(),
    /** Since schema 5. */
    val rawMessages: List<ExportRawMessage> = emptyList(),
    val balanceCheckpoints: List<ExportCheckpoint> = emptyList(),
    /** Since schema 6: the people transfers go to and come from. */
    val people: List<ExportPerson> = emptyList(),
    /** Since schema 7. */
    val loans: List<ExportLoan> = emptyList(),
    /** Since schema 8: subscriptions, bills and planned payments. */
    val recurring: List<ExportRecurring> = emptyList()
) {
    companion object {
        const val SCHEMA_VERSION = 9
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
    val createdAt: String,
    /** Since schema 5: other digits its bank's SMS quote for it (a card on it). */
    val refs: List<ExportAccountRef> = emptyList()
)

@Serializable
data class ExportAccountRef(val institution: String, val ref: String)

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
    val merchantUid: String? = null,
    /** Since schema 5: a foreign charge before conversion, and the SMS it was read from. */
    val originalAmountMinor: Long? = null,
    val originalCurrency: String? = null,
    val rawMessageHash: String? = null
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
    val createdAt: String,
    /** Since schema 5: its other conditions (title holds, account, amount range). */
    val contains: String? = null,
    val accountUid: String? = null,
    val minMinor: Long? = null,
    val maxMinor: Long? = null
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
    val confidence: Int? = null,
    /** Since schema 5: you named it; its automatic rule was already made. */
    val namedByYou: Boolean = false,
    val autoRuled: Boolean = false
)

/** One spelling: its key (lower case, letters only), as first written, and how it joined. */
@Serializable
data class ExportAlias(val key: String, val descriptor: String, val matchedBy: String)

/**
 * Someone you send money to or get it from, and every way a bank writes their name (each a key,
 * as merchants' are, and the name as first written). A transfer is theirs when its title's key
 * is one of them.
 */
@Serializable
data class ExportPerson(
    val uid: String,
    val name: String,
    val namedByYou: Boolean,
    val aliases: List<ExportPersonAlias>
)

@Serializable
data class ExportPersonAlias(val key: String, val descriptor: String)

/**
 * Money lent to (`LENT`) or borrowed from (`BORROWED`) a person, and what happened to it. An
 * event with a transaction takes that transaction's amount and time; one without (`FORGIVENESS`)
 * carries its own.
 */
@Serializable
data class ExportLoan(
    val uid: String,
    val personUid: String,
    val direction: String,
    val currency: String,
    /** ISO-8601 date, or null with no due date. */
    val dueOn: String?,
    val createdAt: String,
    val events: List<ExportLoanEvent>,
    /** Since schema 9: the purchase this is a share of, when a bill was split. */
    val splitOfTransactionUid: String? = null
)

@Serializable
data class ExportLoanEvent(
    val uid: String,
    /** `DISBURSEMENT`, `REPAYMENT` or `FORGIVENESS`. */
    val type: String,
    val transactionUid: String? = null,
    val amountMinor: Long? = null,
    val at: String? = null
)

/**
 * A subscription, bill or planned payment: `amountMinor` every `every` `unit` (DAY, WEEK, MONTH,
 * YEAR) from `anchor`, paid to its merchant or person (by uid) when it has one.
 */
@Serializable
data class ExportRecurring(
    val uid: String,
    val kind: String,
    val name: String,
    val merchantUid: String?,
    val personUid: String?,
    val amountMinor: Long,
    val currency: String,
    val every: Int,
    val unit: String,
    /** ISO-8601 dates. */
    val anchor: String,
    val autoRenew: Boolean,
    val endsOn: String?,
    val reminderDays: Int?,
    val cancelReminder: Boolean,
    val status: String,
    val createdAt: String
)

@Serializable
data class ExportInternalTransfer(
    val uid: String,
    val outTransactionUid: String,
    val inTransactionUid: String,
    val matchConfidence: Double
)

/** A bank SMS exactly as it arrived. [hash] is what transactions and balances point at. */
@Serializable
data class ExportRawMessage(
    val sender: String,
    val body: String,
    /** ISO-8601 instant. */
    val receivedAt: String,
    val hash: String,
    val status: String,
    val parserVersion: Int,
    val unroutedRefs: String? = null
)

/** A balance a bank reported, or one you gave (no message). */
@Serializable
data class ExportCheckpoint(
    val accountUid: String,
    val balanceMinor: Long,
    val at: String,
    val rawMessageHash: String? = null
)
