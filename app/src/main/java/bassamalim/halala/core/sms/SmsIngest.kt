package bassamalim.halala.core.sms

import bassamalim.halala.core.data.dataSources.room.entities.Account
import bassamalim.halala.core.data.dataSources.room.entities.AccountRef
import bassamalim.halala.core.data.dataSources.room.entities.BalanceCheckpoint
import bassamalim.halala.core.data.dataSources.room.entities.RawMessage
import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.data.repositories.InstitutionsRepository
import bassamalim.halala.core.data.repositories.SmsRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.RawStatus
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import bassamalim.halala.core.models.AccountDraft
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
    private val classification: ClassificationRepository,
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
     * again while each pass gets somewhere: a card is learned from an SMS that quotes it
     * beside its account, which may be newer than the SMS that quote the card alone.
     */
    suspend fun processPending(retry: Boolean = false) {
        val statuses = if (retry) listOf(RawStatus.PENDING, RawStatus.UNROUTED) else listOf(RawStatus.PENDING)
        // Another pass, too, while one files something: an SMS that names no account goes to its
        // bank's busiest, which the first pass through an empty ledger can't tell yet.
        do {
            val known = sms.getRefs().size
            val waiting = sms.getRawIds(listOf(RawStatus.UNROUTED)).size
            for (id in sms.getRawIds(statuses)) process(id)
            val progressed = sms.getRefs().size > known || sms.getRawIds(listOf(RawStatus.UNROUTED)).size < waiting
        } while (retry && progressed)
        recordUnsentLegs()
        floorOpeningBalances()
        // The pipeline's last stage: what the rules know is filed as it arrives.
        classification.applyRules()
    }

    /**
     * Money that arrived naming one of your accounts at another bank as its sender ("from
     * ••4444") left that account, whether or not its bank said so. So did money that arrived
     * minutes after another of your banks sent a one-time code for a transfer of that very
     * amount. Once a day has passed with no sending leg to pair, the debit is recorded there,
     * paired as implied.
     */
    // ponytail: re-reads every unpaired arrival on each run; keep a "checked" mark if it gets slow.
    private suspend fun recordUnsentLegs() {
        val all = accounts.getAll()
        val learned = sms.getRefs()
        for (arrival in sms.unpairedArrivals(clock.instant() - SETTLED_AFTER)) {
            val parsed = arrival.rawMessageId?.let { sms.getRaw(it) }
                ?.let { raw -> SmsParser.bankFor(raw.sender)?.let { SmsParser.parse(it, raw.body) } }
                as? ParsedSms.Movement ?: continue
            val here = all.firstOrNull { it.id == arrival.accountId } ?: continue
            val sender = all.singleOrNull {
                it.institutionId != null && it.institutionId != here.institutionId &&
                    it.currency == arrival.currency && isLinked(parsed.partyRefs, it, learned)
            } ?: codeSentFor(arrival, here, all, learned) ?: continue

            val sentId = transactions.addParsed(
                arrival.copy(
                    id = 0,
                    uid = UUID.randomUUID().toString(),
                    accountId = sender.id,
                    direction = Direction.DEBIT,
                    kind = TransactionKind.TRANSFER_OUT
                )
            )
            transactions.pair(sentId, arrival.id, IMPLIED_CONFIDENCE)
        }
    }

    /**
     * The account a transfer left, by the one-time code its bank sent for it: a code for exactly
     * [arrival]'s amount, from another of your banks, in the minutes before it arrived. The code
     * names no account, so it is the one that bank's digitless SMS go to.
     */
    private suspend fun codeSentFor(arrival: Transaction, here: Account, all: List<Account>, learned: List<AccountRef>): Account? {
        val codes = sms.ignoredBetween(arrival.occurredAt - CODE_WINDOW, arrival.occurredAt)
            .filter { SmsParser.oneTimeCodeAmount(it.body) == arrival.amountMinor to arrival.currency }
        return codes.mapNotNull { raw ->
            val institutionId = SmsParser.bankFor(raw.sender)?.let { institutionIdOf(it) } ?: return@mapNotNull null
            if (institutionId == here.institutionId) return@mapNotNull null
            val there = all.filter { it.institutionId == institutionId }
            route(emptyList(), there, learned.filter { it.institutionId == institutionId }, busiestOf(there))
        }.distinctBy { it.id }.singleOrNull()
    }

    /**
     * A bank account can't have been below zero, so one whose messages take it there held at
     * least that much before they began: its opening balance is raised to the least that keeps
     * it at zero or more throughout. The last hour is left out, since a debit's SMS can arrive a
     * moment before the credit that paid for it.
     */
    private suspend fun floorOpeningBalances() {
        val until = clock.instant() - FLOOR_AFTER
        for (account in accounts.getAll()) {
            if (account.institutionId == null) continue
            val lowest = sms.lowestBalance(account.id, until) ?: continue
            if (account.openingBalanceMinor + lowest < 0) accounts.setOpeningBalance(account.id, -lowest)
        }
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
                val here = all.filter { it.institutionId == institutionId }
                val account = route(parsed.ownRefs, here, atBank, busiestOf(here).takeIf { parsed.ownRefs.isEmpty() })
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
        val named = all.firstOrNull { other ->
            parsed.kind == TransactionKind.INTERNAL_TRANSFER && other.id != account.id &&
                other.currency == account.currency && isLinked(parsed.partyRefs, other, learned)
        }
        // An older SMS names only one side ("to ••1111"). The other side is still one of your
        // accounts at that bank, and when only one other was in use by then, it is that one.
        // (Numberless accounts, like Awaeed below, are never what such an SMS leaves unsaid.)
        val implied = if (named != null || parsed.kind != TransactionKind.INTERNAL_TRANSFER || parsed.partyRefs.isNotEmpty()) null
        else all.filter { it.institutionId == account.institutionId && it.id != account.id && it.currency == account.currency && it.last4 != null }
            .let { siblings -> sms.inUseBy(siblings.map(Account::id), at).singleOrNull()?.let { id -> siblings.first { it.id == id } } }
        val product = parsed.into?.let { productAccount(it, account, all, learned) }
        val stated = named ?: product
        val otherSide = stated ?: implied

        val legId = if (otherSide != null) {
            val far = leg.copy(
                uid = UUID.randomUUID().toString(),
                accountId = otherSide.id,
                direction = if (parsed.direction == Direction.DEBIT) Direction.CREDIT else Direction.DEBIT
            )
            val (sent, arrived) = if (leg.direction == Direction.DEBIT) leg to far else far to leg
            if (stated != null) transactions.addParsedPair(sent, arrived)
            else transactions.addParsed(sent).also { transactions.pair(it, transactions.addParsed(arrived), IMPLIED_CONFIDENCE) }
        } else {
            transactions.addParsed(leg).also { id ->
                val recorded = leg.copy(id = id)
                if (!pairWithFarSide(recorded, parsed, all, learned)) recordFarSide(recorded, parsed, all)
            }
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
    private suspend fun pairWithFarSide(leg: Transaction, parsed: ParsedSms.Movement, all: List<Account>, learned: List<AccountRef>): Boolean {
        val outgoing = leg.direction == Direction.DEBIT
        if (leg.kind !in if (outgoing) OUT_KINDS else IN_KINDS) return false

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
        ) ?: return false

        val (sent, arrived) = if (outgoing) leg to best.first else best.first to leg
        if (sent.amountMinor > arrived.amountMinor) transactions.splitFee(sent.id, sent.amountMinor - arrived.amountMinor)
        transactions.pair(sent.id, arrived.id, best.third)
        return true
    }

    /**
     * The SMS says the other side is your own account at [ParsedSms.Movement.farBank], and that
     * bank has sent nothing to pair it with: the other leg is recorded on your one account
     * there. If its own SMS does arrive in the next minutes, it is dropped as the same event.
     */
    private suspend fun recordFarSide(leg: Transaction, parsed: ParsedSms.Movement, all: List<Account>) {
        val institutionId = institutions.getAll().firstOrNull { it.name == parsed.farBank }?.id ?: return
        val there = all.singleOrNull { it.institutionId == institutionId && !it.archived && it.currency == leg.currency } ?: return

        val outgoing = leg.direction == Direction.DEBIT
        val farId = transactions.addParsed(
            leg.copy(
                id = 0,
                uid = UUID.randomUUID().toString(),
                accountId = there.id,
                direction = if (outgoing) Direction.CREDIT else Direction.DEBIT,
                kind = if (outgoing) TransactionKind.TRANSFER_IN else TransactionKind.TRANSFER_OUT
            )
        )
        if (outgoing) transactions.pair(leg.id, farId, IMPLIED_CONFIDENCE)
        else transactions.pair(farId, leg.id, IMPLIED_CONFIDENCE)
    }

    /**
     * Your account for a bank product an SMS names without a number ("Awaeed"), made the first
     * time money goes into it. It is found again by a learned ref, so renaming it is safe.
     */
    private suspend fun productAccount(name: String, beside: Account, all: List<Account>, learned: List<AccountRef>): Account? {
        val institutionId = beside.institutionId ?: return null
        val ref = PRODUCT_REF + name
        learned.firstOrNull { it.institutionId == institutionId && it.ref == ref }
            ?.let { hit -> all.firstOrNull { it.id == hit.accountId } }
            ?.let { return it }

        val id = accounts.create(AccountDraft(institutionId, name, AccountType.SAVINGS, null, beside.currency, 0))
        sms.addRef(institutionId, ref, id)
        return accounts.get(id)
    }

    /** The bank's account in use with the most transactions so far: where its digitless SMS go. */
    private suspend fun busiestOf(atBank: List<Account>): Long? =
        sms.busiestOf(atBank.filter { !it.archived }.map(Account::id))

    private suspend fun institutionIdOf(bank: BankFormat): Long? =
        institutions.getAll().firstOrNull { it.name == bank.institution }?.id

    companion object {
        /** Banks resend within seconds; three minutes is the spec's window. */
        val DUPLICATE_WINDOW: Duration = Duration.ofMinutes(3)
        val PAIR_WINDOW: Duration = Duration.ofHours(48)

        /** How long before an arrival its sender's one-time code can have been sent. */
        val CODE_WINDOW: Duration = Duration.ofMinutes(5)

        /** How long an arrival waits for its sending bank's SMS before that leg is implied. */
        val SETTLED_AFTER: Duration = Duration.ofDays(1)

        /** How old a transaction must be before a dip below zero is believed. */
        val FLOOR_AFTER: Duration = Duration.ofHours(1)
        val CLOSE_WINDOW: Duration = Duration.ofMinutes(10)
        /** The most a sending bank's fee adds to the amount it quotes (2 SAR; SARIE costs up to 1.15). */
        const val FEE_TOLERANCE = 200L
        /** The far side wasn't named, only implied by being the bank's one other account. */
        const val IMPLIED_CONFIDENCE = 0.8
        const val LINKED_CONFIDENCE = 0.9
        const val CLOSE_CONFIDENCE = 0.6

        /** The learned ref for a bank's SMS that quote no digits at all. */
        const val NO_DIGITS = ""

        /** The learned ref that finds a product's account again: "product:Awaeed". */
        const val PRODUCT_REF = "product:"

        /** A top-up is a "purchase" at your other bank, so a purchase can be a move's sending leg. */
        private val OUT_KINDS = setOf(TransactionKind.TRANSFER_OUT, TransactionKind.INTERNAL_TRANSFER, TransactionKind.PURCHASE)
        private val IN_KINDS = setOf(TransactionKind.TRANSFER_IN, TransactionKind.INTERNAL_TRANSFER)

        /**
         * Which of a bank's [accounts] an SMS is about: the first of its digit groups that ends
         * an account's last four or was learned for one. Digits nobody knows are asked about,
         * never guessed. An SMS that quotes none goes to the account you chose for those, else
         * the bank's only account, else its busiest ([busiestId]): most of what a bank sends
         * without a number is everyday traffic on the main account.
         */
        fun route(refs: List<String>, accounts: List<Account>, learned: List<AccountRef>, busiestId: Long? = null): Account? {
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
            return accounts.filter { !it.archived }.singleOrNull() ?: accounts.firstOrNull { it.id == busiestId }
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

        /** The kind of message: its first line, less any numbers in it (an order's number, an amount). */
        private fun header(body: String) = body.trim().lineSequence().first().filterNot(Char::isDigit).trim()

        fun hash(sender: String, body: String, receivedAt: Instant): String =
            MessageDigest.getInstance("SHA-256")
                .digest("$sender\n${receivedAt.toEpochMilli()}\n$body".toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}

