package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.room.daos.LoansDao
import bassamalim.halala.core.data.dataSources.room.daos.TransactionsDao
import bassamalim.halala.core.data.dataSources.room.entities.Loan
import bassamalim.halala.core.data.dataSources.room.entities.LoanEvent
import bassamalim.halala.core.data.dataSources.room.relations.LoanEventRow
import bassamalim.halala.core.domain.LoanState
import bassamalim.halala.core.domain.Loans
import bassamalim.halala.core.domain.Splits
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.LoanDirection
import bassamalim.halala.core.enums.LoanEventType
import bassamalim.halala.core.enums.TransactionKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loans to and from people. A transfer becomes part of one by your say: marking it as lending
 * (or borrowing) opens a loan, and marking a transfer back as repaying it pays it down. Its
 * kind follows (a loan's kinds count as neither spending nor income) and whatever filed it lets
 * go; taking it back out makes it a plain transfer again.
 */
@Singleton
class LoansRepository @Inject constructor(
    private val loansDao: LoansDao,
    private val transactionsDao: TransactionsDao,
    private val clock: Clock
) {

    /** Every loan where it stands, oldest first. */
    fun observeStates(): Flow<List<LoanState>> =
        combine(loansDao.observeLoans(), loansDao.observeEvents(), Loans::statesOf)

    suspend fun getLoans(): List<Loan> = loansDao.getLoans()

    suspend fun getEvents(): List<LoanEvent> = loansDao.getEvents()

    suspend fun getEventFor(transactionId: Long): LoanEvent? = loansDao.getEventFor(transactionId)

    /**
     * [transactionId], a plain transfer, lent (or borrowed) money: a new loan with [personId],
     * or with the person it names when you didn't choose (someone can ask you to pay a third
     * person for them), due [dueOn] if you said. Returns the loan's id, or null when the transfer
     * can't be one (not a plain transfer, nobody named or chosen, or already part of a loan).
     */
    suspend fun open(transactionId: Long, dueOn: LocalDate?, personId: Long? = null): Long? {
        val tx = transactionsDao.get(transactionId) ?: return null
        if (!isFree(transactionId, tx.kind)) return null
        val personId = personId ?: loansDao.getPersonFor(tx.merchantKey) ?: return null
        val direction = LoanDirection.of(tx.direction)

        return loansDao.open(
            loan = Loan(
                uid = UUID.randomUUID().toString(),
                personId = personId,
                direction = direction,
                currency = tx.currency,
                dueOn = dueOn,
                createdAt = clock.instant()
            ),
            eventUid = UUID.randomUUID().toString(),
            transactionId = transactionId,
            kind = Loans.kindOf(LoanEventType.DISBURSEMENT, direction)
        )
    }

    /**
     * [transactionId] pays [loanId] back. It must move money the repaying way, in the loan's
     * currency, and be a plain transfer or a refund not yet part of a loan; false when it isn't.
     */
    suspend fun repay(loanId: Long, transactionId: Long): Boolean {
        val loan = loansDao.getLoan(loanId) ?: return false
        val tx = transactionsDao.get(transactionId) ?: return false
        if (!isFree(transactionId, tx.kind)) return false
        if (tx.direction != loan.direction.repaying || tx.currency != loan.currency) return false

        loansDao.link(
            LoanEvent(
                uid = UUID.randomUUID().toString(),
                loanId = loanId,
                type = LoanEventType.REPAYMENT,
                transactionId = transactionId
            ),
            Loans.kindOf(LoanEventType.REPAYMENT, loan.direction)
        )
        return true
    }

    /**
     * [transactionId] pays back several loans, [shares] (loan id → minor units) of it each. Each
     * loan must be paid back money going this way, in the transfer's currency, and the shares
     * must be the whole transfer; one loan is [repay]. False when it can't be.
     */
    suspend fun repayMany(transactionId: Long, shares: Map<Long, Long>): Boolean {
        if (shares.size == 1) return repay(shares.keys.single(), transactionId)
        val tx = transactionsDao.get(transactionId) ?: return false
        if (shares.isEmpty() || !isFree(transactionId, tx.kind)) return false
        if (Loans.validateShares(tx.amountMinor, shares).isNotEmpty()) return false
        val loans = shares.keys.map { loansDao.getLoan(it) ?: return false }
        if (loans.any { it.direction.repaying != tx.direction || it.currency != tx.currency }) return false

        loansDao.linkAll(
            loans.map { loan ->
                LoanEvent(
                    uid = UUID.randomUUID().toString(),
                    loanId = loan.id,
                    type = LoanEventType.REPAYMENT,
                    transactionId = transactionId,
                    amountMinor = shares.getValue(loan.id)
                )
            },
            transactionId,
            Loans.kindOf(LoanEventType.REPAYMENT, loans.first().direction)
        )
        return true
    }

    /** Lets go of what is still owed on [loanId]: it is settled, as of now. */
    suspend fun forgive(loanId: Long) {
        val loan = loansDao.getLoan(loanId) ?: return
        val remaining = Loans.stateOf(loan, loansDao.getEventRows(loanId)).remainingMinor
        if (remaining <= 0) return
        loansDao.insertEvent(
            LoanEvent(
                uid = UUID.randomUUID().toString(),
                loanId = loanId,
                type = LoanEventType.FORGIVENESS,
                amountMinor = remaining,
                at = clock.instant()
            )
        )
    }

    suspend fun setDueOn(loanId: Long, dueOn: LocalDate?) {
        val loan = loansDao.getLoan(loanId) ?: return
        if (loan.dueOn != dueOn) loansDao.updateLoan(loan.copy(dueOn = dueOn))
    }

    /**
     * "Not part of a loan": [transactionId] is a plain transfer again (a refund that was part of
     * one comes back as money in from a transfer; its kind can be set back on its form). When it was the only
     * money lent, there is no loan left, so the loan goes, and its repayments are plain
     * transfers again too.
     */
    suspend fun unlink(transactionId: Long) {
        val event = loansDao.getEventFor(transactionId) ?: return
        // Repaying (one loan or several): its shares go, and it is a plain transfer again.
        if (event.type != LoanEventType.DISBURSEMENT) {
            val tx = transactionsDao.get(transactionId) ?: return
            return loansDao.unlinkRepayments(transactionId, Loans.plainKind(tx.direction))
        }
        val rows = loansDao.getEventRows(event.loanId)
        val lastLent = event.type == LoanEventType.DISBURSEMENT &&
                rows.none { it.type == LoanEventType.DISBURSEMENT && it.id != event.id }
        val freed = if (lastLent) rows.mapNotNull { it.transactionId } else listOf(transactionId)

        loansDao.unlink(plainKinds(freed, event.loanId), eventId = event.id.takeUnless { lastLent }, loanId = event.loanId.takeIf { lastLent })
    }

    /**
     * Splits [transactionId], a purchase or bill you paid, with others: each person's share
     * ([shares], person → minor units) becomes a loan owed to you, dated the purchase's day and due
     * [dueOn] if you said. One share of the whole is a purchase you paid for someone else. False
     * when it can't be split (not your spending, already split, or shares that don't fit).
     */
    suspend fun split(transactionId: Long, shares: Map<Long, Long>, dueOn: LocalDate? = null): Boolean {
        val tx = transactionsDao.get(transactionId) ?: return false
        if (tx.direction != Direction.DEBIT || !tx.kind.countsInTotals) return false
        if (transactionsDao.getTransferFor(transactionId) != null || loansDao.getSplitOf(transactionId).isNotEmpty()) return false
        if (Splits.validate(tx.amountMinor, shares).isNotEmpty()) return false

        loansDao.split(
            shares.map { (personId, minor) ->
                Loan(
                    uid = UUID.randomUUID().toString(),
                    personId = personId,
                    direction = LoanDirection.LENT,
                    currency = tx.currency,
                    dueOn = dueOn,
                    createdAt = clock.instant(),
                    splitOf = transactionId
                ) to LoanEvent(
                    uid = UUID.randomUUID().toString(),
                    loanId = 0,
                    type = LoanEventType.DISBURSEMENT,
                    amountMinor = minor,
                    at = tx.occurredAt
                )
            }
        )
        return true
    }

    /** The split of [transactionId] undone: its shares' loans go, and their repayments are plain transfers again. */
    suspend fun unsplit(transactionId: Long) {
        for (loan in loansDao.getSplitOf(transactionId)) {
            val freed = loansDao.getEventRows(loan.id).mapNotNull { it.transactionId }
            loansDao.unlink(plainKinds(freed, loan.id), eventId = null, loanId = loan.id)
        }
    }

    /**
     * The plain kinds [ids] go back to as [loanId] lets them go; one that also repays another
     * loan stays that loan's repayment (only its share of this one goes, with the loan).
     */
    private suspend fun plainKinds(ids: List<Long>, loanId: Long): Map<Long, TransactionKind> = ids
        .filter { id -> loansDao.getEventsFor(id).all { it.loanId == loanId } }
        .mapNotNull { id -> transactionsDao.get(id)?.let { id to Loans.plainKind(it.direction) } }
        .toMap()

    /** A plain transfer to or from someone, or a refund: not a move between your own accounts, nor part of a loan. */
    private suspend fun isFree(transactionId: Long, kind: TransactionKind) =
        kind in Loans.MARKABLE && transactionsDao.getTransferFor(transactionId) == null &&
                loansDao.getEventFor(transactionId) == null

    /** The events of one loan, as shown. */
    suspend fun getEventRows(loanId: Long): List<LoanEventRow> = loansDao.getEventRows(loanId)
}
