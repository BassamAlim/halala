package bassamalim.halala.features.reconcileCash

import bassamalim.halala.core.domain.CashGap
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ReconcileCashDomainTest {

    private val at = Instant.parse("2026-09-30T18:00:00Z")

    @Test
    fun `missing cash becomes uncategorised cash spending`() {
        val draft = ReconcileCashDomain.draftFor(1, CashGap.Spent(6_200), at)!!

        assertEquals(Direction.DEBIT, draft.direction)
        assertEquals(6_200L, draft.amountMinor)
        assertEquals(TransactionKind.PURCHASE, draft.kind)
        assertEquals(TransactionSource.RECONCILE, draft.source)
        assertEquals(CashGap.SPENT_TITLE, draft.title)
        assertEquals(at, draft.occurredAt)
    }

    @Test
    fun `extra cash is a correction, not income`() {
        val draft = ReconcileCashDomain.draftFor(1, CashGap.Found(2_000), at)!!

        assertEquals(Direction.CREDIT, draft.direction)
        assertEquals(TransactionKind.ADJUSTMENT, draft.kind)
        assertEquals(false, draft.kind.countsInTotals)
    }

    @Test
    fun `a wallet that matches records nothing`() {
        assertNull(ReconcileCashDomain.draftFor(1, CashGap.None, at))
    }
}
