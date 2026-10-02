package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.Loan
import bassamalim.halala.core.data.dataSources.room.relations.LoanEventRow
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.LoanDirection
import bassamalim.halala.core.enums.LoanEventType
import bassamalim.halala.core.enums.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class LoansTest {

    private val t0 = Instant.parse("2026-09-12T10:00:00Z")

    private fun loan(id: Long, direction: LoanDirection = LoanDirection.LENT, personId: Long = 1) =
        Loan(id, "l$id", personId, direction, "SAR", createdAt = t0)

    private fun event(loanId: Long, type: LoanEventType, minor: Long, days: Long, id: Long = days) =
        LoanEventRow(id, loanId, type, transactionId = null, amountMinor = minor, at = t0.plusSeconds(days * 86_400), kind = null, accountNickname = null, institutionName = null)

    @Test
    fun `what is owed is lent less repaid and forgiven, never below zero`() {
        val state = Loans.stateOf(
            loan(1),
            listOf(
                event(1, LoanEventType.DISBURSEMENT, 150_000, 0),
                event(1, LoanEventType.REPAYMENT, 50_000, 14)
            )
        )
        assertEquals(100_000, state.remainingMinor)
        assertEquals(t0, state.lentAt)
        assertNull(state.settledAt)

        val over = Loans.stateOf(
            loan(1),
            listOf(event(1, LoanEventType.DISBURSEMENT, 1_000, 0), event(1, LoanEventType.REPAYMENT, 1_500, 3))
        )
        assertEquals(0, over.remainingMinor)
        assertEquals(t0.plusSeconds(3 * 86_400), over.settledAt)
    }

    @Test
    fun `a loan with nothing lent is left out`() {
        assertEquals(emptyList<LoanState>(), Loans.statesOf(listOf(loan(1)), listOf(event(1, LoanEventType.REPAYMENT, 5, 1))))
    }

    @Test
    fun `money from someone you lent to suggests their oldest open loan`() {
        val states = Loans.statesOf(
            listOf(loan(1), loan(2), loan(3, LoanDirection.BORROWED)),
            listOf(
                event(1, LoanEventType.DISBURSEMENT, 1_000, 5),
                event(2, LoanEventType.DISBURSEMENT, 1_000, 2),
                event(3, LoanEventType.DISBURSEMENT, 1_000, 1)
            )
        )
        assertEquals(2L, Loans.repaidBy(1, Direction.CREDIT, "SAR", TransactionKind.TRANSFER_IN, states)?.loan?.id)
        assertEquals(3L, Loans.repaidBy(1, Direction.DEBIT, "SAR", TransactionKind.TRANSFER_OUT, states)?.loan?.id)
        assertNull(Loans.repaidBy(2, Direction.CREDIT, "SAR", TransactionKind.TRANSFER_IN, states))
        assertNull(Loans.repaidBy(1, Direction.CREDIT, "USD", TransactionKind.TRANSFER_IN, states))
        assertNull(Loans.repaidBy(1, Direction.CREDIT, "SAR", TransactionKind.LOAN_REPAYMENT, states))
    }

    @Test
    fun `owed sums the open loans each way`() {
        val states = Loans.statesOf(
            listOf(loan(1), loan(2, LoanDirection.BORROWED)),
            listOf(event(1, LoanEventType.DISBURSEMENT, 1_200, 0, id = 1), event(2, LoanEventType.DISBURSEMENT, 300, 0, id = 2))
        )
        assertEquals(1_200L to 300L, Loans.owed(states, "SAR"))
    }
}
