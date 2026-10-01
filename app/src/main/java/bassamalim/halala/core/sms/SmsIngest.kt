package bassamalim.halala.core.sms

import bassamalim.halala.core.data.dataSources.room.entities.Account
import bassamalim.halala.core.data.dataSources.room.entities.AccountRef
import bassamalim.halala.core.data.dataSources.room.entities.BalanceCheckpoint
import bassamalim.halala.core.data.dataSources.room.entities.RawMessage
import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.InstitutionsRepository
import bassamalim.halala.core.data.repositories.SmsRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.RawStatus
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

/**
 * The spec's ingestion pipeline, the same for a live SMS and the back-import: store the raw
 * message, then parse, route to one of your accounts, drop a duplicate, record, pair the two
 * sides of a move, and keep any balance the bank reported.
 *
 * Messages are stored first and processed after, so nothing is lost if processing fails, and
 * one that can't be routed yet waits as UNROUTED until you say which account its digits are.
 */
@Singleton
class SmsIngest @Inject constructor(
    private val sms: SmsRepository,
    private val transactions: TransactionsRepository,
    private val accounts: AccountsRepository,
    private val institutions: InstitutionsRepository,
    private val clock: Clock
) {

    /** Keeps a bank SMS for processing. Null for a sender that isn't a bank, or one already kept. */
    suspend fun store(sender: String, body: String, receivedAt: Instant): Long? {
        SmsParser.bankFor(sender) ?: return null
        return sms.insertRaw(
            RawMessage(
                sender = sender.trim(),
                body = body,
                receivedAt = receivedAt,
                hash = hash(sender.trim(), body, receivedAt)
            )
        )
    }

    /**
     * Runs what is waiting through the pipeline, oldest first. Safe to call any time. With
     * [retry], messages no account matched are tried again (after accounts were named), and
     * again while each pass learns new digits: a card is learned from an SMS that quotes it
     * beside its account, which may be newer than the SMS that quote the card alone.
     */
    suspend fun processPending(retry: Boolean = false) {
        val statuses = if (retry) listOf(RawStatus.PENDING, RawStatus.UNROUTED) else listOf(RawStatus.PENDING)
        do {
            val known = sms.getRefs().size
            for (id in sms.getRawIds(statuses)) process(id)
        } while (retry && sms.getRefs().size > known)
    }

    /**
     * Your answer to "Which account is ••[ref]?" for [sender]'s bank ([ref] null for SMS that
     * quote no digits): remembered, then everything waiting is tried again.
     */
    suspend fun assign(sender: String, ref: String?, accountId: Long) {
        val institutionId = institutionIdOf(SmsParser.bankFor(sender) ?: return) ?: return
        sms.addRef(institutionId, ref ?: NO_DIGITS, accountId)
        processPending(retry = true)
    }

    private suspend fun process(id: Long) {
        val raw = sms.getRaw(id) ?: return
        val bank = SmsParser.bankFor(raw.sender)
        val status = when (val parsed = bank?.let { SmsParser.parse(it, raw.body) }) {
            null, ParsedSms.Ignored -> RawStatus.IGNORED
            ParsedSms.Declined -> RawStatus.DECLINED
            ParsedSms.Unrecognised -> RawStatus.UNRECOGNISED
            is ParsedSms.Movement -> {
                val institutionId = institutionIdOf(bank) ?: return
                val learned = sms.getRefs()
                val all = accounts.getAll()
                val atBank = learned.filter { it.institutionId == institutionId }
                val account = route(parsed.ownRefs, all.filter { it.institutionId == institutionId }, atBank)
                    ?: return sms.setStatus(id, RawStatus.UNROUTED, BankFormats.PARSER_VERSION, parsed.ownRefs.joinToString(","))
                // A card quoted beside its account ("من:1111 بطاقة:9001") is learned for it, so
                // the SMS that quote only the card find their way without asking.
                for (ref in parsed.ownRefs)
                    if (ref.length == 4 && account.last4 != ref && atBank.none { it.ref == ref })
                        sms.addRef(institutionId, ref, account.id)
                record(raw, parsed, account, all, learned)
            }
        }
        sms.setStatus(id, status, BankFormats.PARSER_VERSION)
    }

    private suspend fun record(
        raw: RawMessage,
        parsed: ParsedSms.Movement,
        account: Account,
        all: List<Account>,
        learned: List<AccountRef>
    ): RawStatus {
        if (parsed.currency != account.currency) return RawStatus.FOREIGN

        val at = raw.receivedAt
        val similar = sms.findSimilar(account.id, parsed.direction, parsed.amountMinor, at - DUPLICATE_WINDOW, at + DUPLICATE_WINDOW)
        if (similar.any { it.rawMessageId == raw.id }) return RawStatus.RECORDED // already done, before a crash
        if (similar.any { isDuplicate(raw.body, it.rawMessageId?.let { id -> sms.getRaw(id) }?.body) })
            return RawStatus.DUPLICATE

        val leg = Transaction(
            uid = UUID.randomUUID().toString(),
            accountId = account.id,
            direction = parsed.direction,
            amountMinor = parsed.amountMinor,
            currency = account.currency,
            occurredAt = at,
            kind = parsed.kind,
            title = parsed.title,
            source = TransactionSource.SMS,
            createdAt = clock.instant(),
            rawMessageId = raw.id,
            originalAmountMinor = parsed.originalMinor,
            originalCurrency = parsed.originalCurrency
        )

        // "Between your accounts": one SMS names both sides, so both legs are recorded at once.
        val otherSide = all.firstOrNull { other ->
            parsed.kind == TransactionKind.INTERNAL_TRANSFER && other.id != account.id &&
                other.currency == account.currency && isLinked(parsed.partyRefs, other, learned)
        }
        val legId = if (otherSide != null) {
            val far = leg.copy(
                uid = UUID.randomUUID().toString(),
                accountId = otherSide.id,
                direction = if (parsed.direction == Direction.DEBIT) Direction.CREDIT else Direction.DEBIT
            )
            if (leg.direction == Direction.DEBIT) transactions.addParsedPair(leg, far)
            else transactions.addParsedPair(far, leg)
        } else {
            transactions.addParsed(leg).also { pairWithFarSide(leg.copy(id = it), parsed, all, learned) }
        }

        if (parsed.feeMinor > 0) transactions.addParsed(
            leg.copy(
                uid = UUID.randomUUID().toString(),
                direction = Direction.DEBIT,
                amountMinor = parsed.feeMinor,
                kind = TransactionKind.FEE,
                originalAmountMinor = null,
                originalCurrency = null
            )
        )
        if (parsed.balanceMinor != null)
            sms.addCheckpoint(BalanceCheckpoint(accountId = account.id, balanceMinor = parsed.balanceMinor, at = at, rawMessageId = raw.id))

        check(legId > 0)
        return RawStatus.RECORDED
    }

    /**
     * Pairs [leg] with the other side of a move between your accounts, if it was recorded: an
     * unpaired leg the other way on another account, for the same amount or the amount plus the
     * sender's fee (which then becomes its own debit). Either SMS naming the other
     * account's digits pairs them within 48 hours; two plain transfers pair within 10 minutes.
     */
    private suspend fun pairWithFarSide(leg: Transaction, parsed: ParsedSms.Movement, all: List<Account>, learned: List<AccountRef>) {
        val outgoing = leg.direction == Direction.DEBIT
        if (leg.kind !in if (outgoing) OUT_KINDS else IN_KINDS) return

        val candidates = sms.findUnpaired(
            accountId = leg.accountId,
            direction = if (outgoing) Direction.CREDIT else Direction.DEBIT,
            // The sending bank may quote its fee inside the amount (1,250.25 leaves, 1,250 arrives).
            amounts = if (outgoing) leg.amountMinor - FEE_TOLERANCE..leg.amountMinor
            else leg.amountMinor..leg.amountMinor + FEE_TOLERANCE,
            currency = leg.currency,
            from = leg.occurredAt - PAIR_WINDOW,
            to = leg.occurredAt + PAIR_WINDOW
        ).filter { it.kind in if (outgoing) IN_KINDS else OUT_KINDS }

        val ours = parsed.ownRefs + parsed.partyRefs
        val best = candidates.mapNotNull { other ->
            val otherAccount = all.firstOrNull { it.id == other.accountId } ?: return@mapNotNull null
            val theirs = other.rawMessageId?.let { sms.getRaw(it) }
                ?.let { raw -> SmsParser.bankFor(raw.sender)?.let { SmsParser.parse(it, raw.body) } }
                .let { it as? ParsedSms.Movement }
                ?.let { it.ownRefs + it.partyRefs }
                .orEmpty()
            val thisAccount = all.first { it.id == leg.accountId }
            val linked = isLinked(ours, otherAccount, learned) || isLinked(theirs, thisAccount, learned)
            val gap = Duration.between(leg.occurredAt, other.occurredAt).abs()
            val plainTransfers = leg.kind != TransactionKind.PURCHASE && other.kind != TransactionKind.PURCHASE
            when {
                linked -> Triple(other, gap, LINKED_CONFIDENCE)
                plainTransfers && gap <= CLOSE_WINDOW -> Triple(other, gap, CLOSE_CONFIDENCE)
                else -> null
            }
        }.minWithOrNull(
            compareByDescending<Triple<Transaction, Duration, Double>> { it.third }
                .thenBy { (it.first.amountMinor - leg.amountMinor).absoluteValue }
                .thenBy { it.second }
        ) ?: return

        val (sent, arrived) = if (outgoing) leg to best.first else best.first to leg
        if (sent.amountMinor > arrived.amountMinor) transactions.splitFee(sent.id, sent.amountMinor - arrived.amountMinor)
        transactions.pair(sent.id, arrived.id, best.third)
    }

    private suspend fun institutionIdOf(bank: BankFormat): Long? =
        institutions.getAll().firstOrNull { it.name == bank.institution }?.id

    companion object {
        /** Banks resend within seconds; three minutes is the spec's window. */
        val DUPLICATE_WINDOW: Duration = Duration.ofMinutes(3)
        val PAIR_WINDOW: Duration = Duration.ofHours(48)
        val CLOSE_WINDOW: Duration = Duration.ofMinutes(10)
        /** The most a sending bank's fee adds to the amount it quotes (2 SAR; SARIE costs up to 1.15). */
        const val FEE_TOLERANCE = 200L
        const val LINKED_CONFIDENCE = 0.9
        const val CLOSE_CONFIDENCE = 0.6

        /** The learned ref for a bank's SMS that quote no digits at all. */
        const val NO_DIGITS = ""

        /** A top-up is a "purchase" at your other bank, so a purchase can be a move's sending leg. */
        private val OUT_KINDS = setOf(TransactionKind.TRANSFER_OUT, TransactionKind.INTERNAL_TRANSFER, TransactionKind.PURCHASE)
        private val IN_KINDS = setOf(TransactionKind.TRANSFER_IN, TransactionKind.INTERNAL_TRANSFER)

        /**
         * Which of a bank's [accounts] an SMS is about: the first of its digit groups that ends
         * an account's last four or was learned for one. A bank's only account takes an SMS that
         * quotes no digits; digits nobody knows are asked about, never guessed.
         */
        fun route(refs: List<String>, accounts: List<Account>, learned: List<AccountRef>): Account? {
            for (ref in refs) {
                learned.firstOrNull { it.ref == ref }
                    ?.let { hit -> accounts.firstOrNull { it.id == hit.accountId } }
                    ?.let { return it }
                accounts.singleOrNull { it.last4?.endsWith(ref) == true }?.let { return it }
            }
            if (refs.isNotEmpty()) return null
            learned.firstOrNull { it.ref == NO_DIGITS }
                ?.let { hit -> accounts.firstOrNull { it.id == hit.accountId } }
                ?.let { return it }
            return accounts.filter { !it.archived }.singleOrNull()
        }

        /** Whether any of [refs] names [account]: its last four, or digits learned for it. */
        fun isLinked(refs: List<String>, account: Account, learned: List<AccountRef>): Boolean =
            refs.any { ref ->
                account.last4?.endsWith(ref) == true ||
                    learned.any { it.accountId == account.id && it.ref == ref }
            }

        /**
         * Whether two SMS for the same amount, account and direction minutes apart are one
         * event: sent twice word for word, or as two kinds of message (a card hold and its
         * capture, or Al Rajhi Capital's English and Arabic pair). Two alike purchases a minute
         * apart are real, so they are both kept.
         */
        fun isDuplicate(body: String, other: String?): Boolean =
            other != null && (body == other || header(body) != header(other))

        private fun header(body: String) = body.trim().lineSequence().first().trim()

        fun hash(sender: String, body: String, receivedAt: Instant): String =
            MessageDigest.getInstance("SHA-256")
                .digest("$sender\n${receivedAt.toEpochMilli()}\n$body".toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}

