package bassamalim.halala.core.sms

import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind

/** What a labelled line of a bank SMS holds. Which side is "yours" depends on the direction. */
enum class Role {
    AMOUNT,
    FROM,
    TO,
    CARD,
    /** Always one of your accounts. */
    ACCOUNT,
    /** Always the other side's account. */
    PARTY,
    /** The merchant, biller or place. */
    AT,
    FEE,
    /** What the account was charged in its own currency, fees included. */
    TOTAL,
    BALANCE
}

/**
 * One kind of message a bank sends, known by how its first line starts (case-sensitive: a
 * bank's "Local transfer" and "Local Transfer" can mean opposite directions).
 *
 * A null [direction] is inferred from which side quotes an account number, and [kind] becomes
 * TRANSFER_OUT or TRANSFER_IN to match. A null [kind] (and not [declined]) is a message that
 * moves no money.
 */
data class Template(
    val header: String,
    val kind: TransactionKind? = null,
    val direction: Direction? = null,
    val declined: Boolean = false
)

/** How one bank writes its SMS. [institution] is the seeded Institution's name. */
class BankFormat(
    val institution: String,
    val senders: Set<String>,
    templates: List<Template>,
    labels: Map<String, Role>
) {
    /** Longest first, so "حوالة محلية واردة" wins over "حوالة محلية". */
    val templates = templates.sortedByDescending { it.header.length }
    val labels = labels.entries.sortedByDescending { it.key.length }.map { it.key to it.value }
}

sealed interface ParsedSms {
    /**
     * Money that moved. [amountMinor] is in [currency], which is what the account was charged
     * in; a foreign charge keeps what the merchant asked for in [originalMinor]/[originalCurrency].
     * [ownRefs] are digit groups that may end your account's or card's number; [partyRefs] the
     * other side's.
     */
    data class Movement(
        val kind: TransactionKind,
        val direction: Direction,
        val amountMinor: Long,
        val currency: String,
        val ownRefs: List<String> = emptyList(),
        val partyRefs: List<String> = emptyList(),
        val title: String = "",
        val feeMinor: Long = 0,
        val originalMinor: Long? = null,
        val originalCurrency: String? = null,
        val balanceMinor: Long? = null
    ) : ParsedSms

    /** A declined card or transfer: no money moved, kept for anomaly alerts. */
    data object Declined : ParsedSms

    /** OTPs, logins, beneficiaries, notices: nothing to record. */
    data object Ignored : ParsedSms

    /** Quotes an amount but matches no template: a format the parsers don't know yet. */
    data object Unrecognised : ParsedSms
}

/**
 * Turns a bank SMS into what it means, deterministically. Every bank's messages are a header
 * line that says what happened, then "label: value" lines (sometimes without the colon, as in
 * "من1111" or "لـJahez"); [BankFormats] maps both per bank.
 */
object SmsParser {

    fun bankFor(sender: String): BankFormat? =
        BankFormats.ALL.firstOrNull { bank -> bank.senders.any { it.equals(sender.trim(), ignoreCase = true) } }

    fun parse(bank: BankFormat, body: String): ParsedSms {
        val lines = INVISIBLE.replace(body, "").lines().map(String::trim).filter(String::isNotEmpty)
        if (lines.isEmpty() || ONE_TIME_CODE.containsMatchIn(body)) return ParsedSms.Ignored

        val template = bank.templates.firstOrNull { lines[0].startsWith(it.header) }
            ?: return if (lines.any { money(it) != null }) ParsedSms.Unrecognised else ParsedSms.Ignored
        if (template.declined) return ParsedSms.Declined
        val templateKind = template.kind ?: return ParsedSms.Ignored

        val fields = mutableMapOf<Role, MutableList<String>>()
        val unlabelled = mutableListOf(lines[0])
        for (line in lines.drop(1)) {
            val (label, role) = bank.labels.firstOrNull { (label, _) -> line.startsWithLabel(label) }
                ?: run { unlabelled += line; continue }
            fields.getOrPut(role, ::mutableListOf) += line.substring(label.length).trimStart(' ', ':').trim()
        }
        fun refsOf(role: Role) = fields[role].orEmpty().flatMap(::refs)
        fun bareRefsOf(role: Role) = fields[role].orEmpty().filter { LEADING_REF.containsMatchIn(it) }.flatMap(::refs)
        fun nameOf(role: Role) = fields[role].orEmpty().firstNotNullOfOrNull(::name)
        fun moneyOf(role: Role, currency: String) =
            fields[role].orEmpty().firstNotNullOfOrNull { money(it) ?: bare(it, currency) }

        val direction = template.direction
            ?: if (refsOf(Role.TO).isNotEmpty() && refsOf(Role.FROM).isEmpty()) Direction.CREDIT else Direction.DEBIT
        val kind = when {
            template.direction != null -> templateKind
            direction == Direction.CREDIT -> TransactionKind.TRANSFER_IN
            else -> TransactionKind.TRANSFER_OUT
        }

        val stated = moneyOf(Role.AMOUNT, DEFAULT_CURRENCY)
            ?: unlabelled.firstNotNullOfOrNull(::money)
            ?: moneyOf(Role.FEE, DEFAULT_CURRENCY).takeIf { kind == TransactionKind.FEE }
            ?: return ParsedSms.Unrecognised
        val total = moneyOf(Role.TOTAL, stated.second)
        val charged = total ?: stated
        val foreign = total != null && total.second != stated.second
        val credit = direction == Direction.CREDIT
        val toppedUp = credit && kind == TransactionKind.TRANSFER_IN

        return ParsedSms.Movement(
            kind = kind,
            direction = direction,
            amountMinor = charged.first,
            currency = charged.second,
            // Your side of a transfer leads with its digits ("من:1111", "To: ***9003; VISA"); digits
            // trailing a name there are a shop's ("من aldaji1234"). The card that tops up a wallet
            // is the other bank's.
            ownRefs = (refsOf(Role.ACCOUNT) + bareRefsOf(if (credit) Role.TO else Role.FROM) +
                (if (toppedUp) emptyList() else refsOf(Role.CARD))).distinct(),
            partyRefs = (refsOf(Role.PARTY) + refsOf(if (credit) Role.FROM else Role.TO) +
                (if (toppedUp) refsOf(Role.CARD) else emptyList())).distinct(),
            title = fields[Role.AT].orEmpty().firstNotNullOfOrNull(::merchant)
                ?: (if (credit) nameOf(Role.FROM) else nameOf(Role.TO))
                ?: nameOf(Role.PARTY)
                ?: (if (credit) null else nameOf(Role.FROM))
                ?: "",
            // A total already includes the fees; so does a fee charged on its own.
            feeMinor = if (total != null || kind == TransactionKind.FEE) 0
            else moneyOf(Role.FEE, charged.second)?.takeIf { it.second == charged.second }?.first ?: 0,
            originalMinor = stated.first.takeIf { foreign },
            originalCurrency = stated.second.takeIf { foreign },
            balanceMinor = moneyOf(Role.BALANCE, charged.second)?.takeIf { it.second == charged.second }?.first
        )
    }

    /** The first amount with a currency in [text]: "SR 3000", "40.46 SAR", "100SR", "SAR 2,500.00". */
    fun money(text: String): Pair<Long, String>? = MONEY.findAll(text).firstNotNullOfOrNull { match ->
        val (codeBefore, numberAfter, numberBefore, codeAfter) = match.destructured
        val code = (codeBefore.ifEmpty { codeAfter }).let { if (it == "SR") "SAR" else it }
        if (!Money.isCurrency(code)) return@firstNotNullOfOrNull null
        Money.parse(numberAfter.ifEmpty { numberBefore }, code)?.let { it to code }
    }

    /** An amount with no currency ("Amount2700.0"), in the [currency] the message implies. */
    private fun bare(text: String, currency: String): Pair<Long, String>? =
        Money.parse(text, currency)?.let { it to currency }

    /**
     * Digit groups that can end an account or card number: "1111", "*9004", "7700;AHMED", and
     * both halves of SNB's "555*690", since only the ledger knows which one is the account.
     * Longer numbers (a full account or IBAN) are cut to their last four.
     */
    private fun refs(value: String): List<String> =
        REF.findAll(value).map { it.value.takeLast(4) }.toList()

    /** The words in a value, without its account digits: "7700;AHMED ALI" → "AHMED ALI". */
    private fun name(value: String): String? =
        REF.replace(value, " ")
            .replace("*", " ")
            .replace(NULL_WORD, " ")
            .replace(SPACES, " ")
            .trim(' ', ';', ',', '-', '.')
            .takeIf { text -> text.any(Char::isLetter) }

    /** A merchant as the bank wrote it, branch numbers and all; null for a date in its place. */
    private fun merchant(value: String): String? =
        value.takeUnless { DATE.containsMatchIn(it) }?.replace(SPACES, " ")?.takeIf { it.any(Char::isLetter) }

    /** An English label must end at a word boundary ("To" is not "Total"); Arabic ones run on. */
    private fun String.startsWithLabel(label: String): Boolean {
        if (!startsWith(label, ignoreCase = true)) return false
        val next = getOrNull(label.length) ?: return true
        return !label.last().isAsciiLetter() || !next.isLetter()
    }

    private fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'

    private const val DEFAULT_CURRENCY = "SAR"
    private val MONEY = Regex("""(?<![A-Za-z])(?:(SR|[A-Z]{3})\s*(\d[\d,]*(?:\.\d+)?)|(\d[\d,]*(?:\.\d+)?)\s*(SR|[A-Z]{3}))(?![A-Za-z])""")
    private val REF = Regex("""(?<![\d/.])\d{3,}(?![\d/:.])""")
    /** OTPs quote amounts too; they confirm a transaction that gets its own SMS. */
    private val ONE_TIME_CODE =
        Regex("OTP|One Time Password|رمز|كلمة مرور|الرقم السري", RegexOption.IGNORE_CASE)
    private val DATE = Regex("""^\d{1,4}[/-]\d{1,2}[/-]\d{1,4}""")
    private val LEADING_REF = Regex("""^[\s*]*\d""")
    private val NULL_WORD = Regex("""\bnull\b""")
    private val SPACES = Regex("""\s+""")

    /** Bidi marks the banks sprinkle into their SMS (U+061C, U+200E/F, U+202A–E, U+2066–9). */
    private val INVISIBLE = Regex("[؜‎‏‪-‮⁦-⁩]")
}
