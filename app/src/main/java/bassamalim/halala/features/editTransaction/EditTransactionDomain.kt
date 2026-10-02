package bassamalim.halala.features.editTransaction

import bassamalim.halala.core.data.dataSources.room.relations.AccountWithBalance
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.models.TransactionDraft
import bassamalim.halala.core.models.TransferDraft
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/** Money out of an account, into one, or between two of yours. */
enum class EntryMode { OUT, IN, MOVE }

/** What the transaction form says before it is checked. */
data class TransactionForm(
    val mode: EntryMode = EntryMode.OUT,
    val amount: String = "",
    val accountId: Long? = null,
    /** The receiving account of a move. */
    val toAccountId: Long? = null,
    val kind: TransactionKind = TransactionKind.PURCHASE,
    val title: String = "",
    val note: String = "",
    val date: LocalDate,
    val time: LocalTime
)

/** What the form needs to know about an account. */
data class AccountOption(val id: Long, val label: String, val currency: String, val isCash: Boolean)

sealed interface TransactionProblem {
    data object AmountInvalid : TransactionProblem
    data object AccountMissing : TransactionProblem
    data object SameAccount : TransactionProblem
    data object CurrencyMismatch : TransactionProblem
}

/** What a checked form turns into. */
sealed interface CheckedEntry {
    data class Single(val draft: TransactionDraft) : CheckedEntry
    data class Move(val draft: TransferDraft) : CheckedEntry
    data class Invalid(val problems: Set<TransactionProblem>) : CheckedEntry
}

class EditTransactionDomain @Inject constructor(
    private val accountsRepository: AccountsRepository,
    private val transactionsRepository: TransactionsRepository,
    private val classificationRepository: ClassificationRepository,
    private val clock: Clock
) {

    fun observeAccounts(): Flow<List<AccountWithBalance>> = accountsRepository.observeAll()

    fun now(): LocalDateTime = LocalDateTime.now(clock)

    suspend fun cashWalletId(): Long? = accountsRepository.getCashWallet()?.id

    /** The form for an existing transaction; a move is loaded whole, from either leg. */
    suspend fun load(id: Long): TransactionForm? {
        val tx = transactionsRepository.get(id) ?: return null
        val pair = transactionsRepository.getTransferFor(id)
        val local = tx.occurredAt.atZone(clock.zone)

        if (pair != null) {
            val outLeg = transactionsRepository.get(pair.outTransactionId) ?: return null
            val inLeg = transactionsRepository.get(pair.inTransactionId) ?: return null

            return TransactionForm(
                mode = EntryMode.MOVE,
                amount = Money.plain(outLeg.amountMinor, outLeg.currency),
                accountId = outLeg.accountId,
                toAccountId = inLeg.accountId,
                kind = outLeg.kind,
                title = outLeg.title,
                note = outLeg.note,
                date = local.toLocalDate(),
                time = local.toLocalTime().withSecond(0).withNano(0)
            )
        }

        return TransactionForm(
            mode = if (tx.direction == Direction.DEBIT) EntryMode.OUT else EntryMode.IN,
            amount = Money.plain(tx.amountMinor, tx.currency),
            accountId = tx.accountId,
            kind = tx.kind,
            title = tx.title,
            note = tx.note,
            date = local.toLocalDate(),
            time = local.toLocalTime().withSecond(0).withNano(0)
        )
    }

    /** Checks and writes. [id] 0 creates; otherwise the transaction, or both legs, are rewritten. */
    suspend fun save(id: Long, form: TransactionForm, accounts: List<AccountOption>): Set<TransactionProblem> {
        when (val checked = validate(form, accounts, clock.zone)) {
            is CheckedEntry.Invalid -> return checked.problems

            is CheckedEntry.Single ->
                if (id == 0L) transactionsRepository.add(checked.draft)
                else transactionsRepository.update(id, checked.draft)

            is CheckedEntry.Move ->
                if (id == 0L) transactionsRepository.addTransfer(checked.draft)
                else transactionsRepository.updateTransfer(id, checked.draft)
        }

        // A merchant the rules know is filed as soon as it is written down.
        classificationRepository.applyRules()
        return emptySet()
    }

    companion object {

        /** The kinds offered for a mode; a move's kind follows from its accounts instead. */
        fun kindsFor(mode: EntryMode): List<TransactionKind> = when (mode) {
            EntryMode.OUT -> TransactionKind.MANUAL_OUT
            EntryMode.IN -> TransactionKind.MANUAL_IN
            EntryMode.MOVE -> emptyList()
        }

        /**
         * The rules, without storage: an amount above zero in the (sending) account's currency;
         * an account; and for a move, a second, different account in the same currency (a
         * conversion needs a rate, which arrives with SMS parsing).
         */
        fun validate(form: TransactionForm, accounts: List<AccountOption>, zone: ZoneId): CheckedEntry {
            val problems = mutableSetOf<TransactionProblem>()
            val from = accounts.firstOrNull { it.id == form.accountId }
            val to = accounts.firstOrNull { it.id == form.toAccountId }

            if (from == null) problems += TransactionProblem.AccountMissing

            val amount = from?.let { Money.parse(form.amount, it.currency) }
            if (from != null && (amount == null || amount <= 0)) problems += TransactionProblem.AmountInvalid

            if (form.mode == EntryMode.MOVE) {
                when {
                    to == null -> problems += TransactionProblem.AccountMissing
                    from != null && to.id == from.id -> problems += TransactionProblem.SameAccount
                    from != null && to.currency != from.currency -> problems += TransactionProblem.CurrencyMismatch
                }
            }

            if (problems.isNotEmpty() || from == null || amount == null) return CheckedEntry.Invalid(problems)

            val occurredAt = LocalDateTime.of(form.date, form.time).atZone(zone).toInstant()

            if (form.mode == EntryMode.MOVE) {
                return CheckedEntry.Move(
                    TransferDraft(
                        fromAccountId = from.id,
                        toAccountId = to!!.id,
                        amountMinor = amount,
                        occurredAt = occurredAt,
                        kind = TransactionKind.forMove(fromCash = from.isCash, toCash = to.isCash),
                        title = form.title,
                        note = form.note
                    )
                )
            }

            return CheckedEntry.Single(
                TransactionDraft(
                    accountId = from.id,
                    direction = if (form.mode == EntryMode.OUT) Direction.DEBIT else Direction.CREDIT,
                    amountMinor = amount,
                    occurredAt = occurredAt,
                    kind = form.kind.takeIf { it in kindsFor(form.mode) } ?: kindsFor(form.mode).first(),
                    title = form.title,
                    note = form.note
                )
            )
        }

        fun isCash(account: AccountWithBalance) = account.account.type == AccountType.CASH
    }
}
