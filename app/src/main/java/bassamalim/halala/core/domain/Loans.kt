package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.Loan
import bassamalim.halala.core.data.dataSources.room.relations.LoanEventRow
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.LoanDirection
import bassamalim.halala.core.enums.LoanEventType
import bassamalim.halala.core.enums.TransactionKind
import java.time.Instant

/** Where a loan stands, from what happened to it. Amounts in the loan's currency, minor units. */
data class LoanState(
    val loan: Loan,
    val lentMinor: Long,
    val repaidMinor: Long,
    val forgivenMinor: Long,
    /** What is still owed: never below zero, so paying back more settles it and no more. */
    val remainingMinor: Long,
    /** When the first money was lent; null for a loan whose transfer was deleted. */
    val lentAt: Instant?,
    /** When it was settled: the last event, once nothing is owed. */
    val settledAt: Instant?,
    val events: List<LoanEventRow>
) {
    val isOpen get() = remainingMinor > 0
}

/**
 * Loans: money lent to or borrowed from a person. A loan is what its events say: a transfer
 * marked as lending (or borrowing), transfers back marked as repaying it, and what you forgave.
 */
object Loans {

    /** The kind a transfer becomes once it is part of a loan. */
    fun kindOf(type: LoanEventType, direction: LoanDirection): TransactionKind = when (type) {
        LoanEventType.DISBURSEMENT -> if (direction == LoanDirection.LENT) TransactionKind.LOAN_GIVEN else TransactionKind.LOAN_RECEIVED
        else -> TransactionKind.LOAN_REPAYMENT
    }

    /** The kind it goes back to when it is no longer part of a loan: a plain transfer. */
    fun plainKind(direction: Direction): TransactionKind =
        if (direction == Direction.DEBIT) TransactionKind.TRANSFER_OUT else TransactionKind.TRANSFER_IN

    /** The transfers that can be marked as lending or borrowing: plain ones, to or from someone. */
    val MARKABLE = setOf(TransactionKind.TRANSFER_OUT, TransactionKind.TRANSFER_IN)

    fun stateOf(loan: Loan, events: List<LoanEventRow>): LoanState {
        fun total(type: LoanEventType) = Money.sum(events.filter { it.type == type }.map { it.amountMinor })
        val lent = total(LoanEventType.DISBURSEMENT)
        val repaid = total(LoanEventType.REPAYMENT)
        val forgiven = total(LoanEventType.FORGIVENESS)
        val remaining = maxOf(0, Math.subtractExact(Math.subtractExact(lent, repaid), forgiven))
        return LoanState(
            loan = loan,
            lentMinor = lent,
            repaidMinor = repaid,
            forgivenMinor = forgiven,
            remainingMinor = remaining,
            lentAt = events.filter { it.type == LoanEventType.DISBURSEMENT }.minOfOrNull { it.at },
            settledAt = if (remaining == 0L && lent > 0) events.maxOfOrNull { it.at } else null,
            events = events.sortedWith(compareBy({ it.at }, { it.id }))
        )
    }

    /**
     * Every loan where it stands, oldest first. A loan with nothing lent (its transfer was
     * deleted) is left out: there is nothing to owe.
     */
    fun statesOf(loans: List<Loan>, events: List<LoanEventRow>): List<LoanState> {
        val byLoan = events.groupBy { it.loanId }
        return loans
            .map { stateOf(it, byLoan[it.id].orEmpty()) }
            .filter { it.lentMinor > 0 }
            .sortedWith(compareBy({ it.lentAt }, { it.loan.id }))
    }

    /**
     * The loan a transfer would repay, to suggest: the oldest still open with the same person,
     * in its currency, that money going this way pays back. Null for a transfer already part of
     * a loan, or not a plain one.
     */
    fun repaidBy(
        personId: Long?,
        direction: Direction,
        currency: String,
        kind: TransactionKind,
        states: List<LoanState>
    ): LoanState? {
        if (personId == null || kind !in MARKABLE) return null
        return states.firstOrNull {
            it.isOpen && it.loan.personId == personId && it.loan.currency == currency &&
                    it.loan.direction.repaying == direction
        }
    }

    /** What is owed to you and what you owe, over the open loans in [currency]. */
    fun owed(states: List<LoanState>, currency: String): Pair<Long, Long> {
        val open = states.filter { it.isOpen && it.loan.currency == currency }
        return Money.sum(open.filter { it.loan.direction == LoanDirection.LENT }.map { it.remainingMinor }) to
                Money.sum(open.filter { it.loan.direction == LoanDirection.BORROWED }.map { it.remainingMinor })
    }
}
