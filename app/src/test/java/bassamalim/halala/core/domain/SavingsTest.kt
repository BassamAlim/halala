package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.SavingsTerms
import bassamalim.halala.core.enums.MaturityChoice
import bassamalim.halala.core.enums.SavingsKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SavingsTest {

    private val today = LocalDate.parse("2026-10-14")

    @Test
    fun `a term earns its rate over its months, and a renewing one rolls on`() {
        val terms = SavingsTerms(1, SavingsKind.AWAEED, "4.40", LocalDate.parse("2026-05-14"), 6, MaturityChoice.RENEW_WITH_PROFIT)
        val term = Savings.termOf(terms, 5_000_000, today)!!
        assertEquals(LocalDate.parse("2026-11-14"), term.maturity)
        // 50,000 × 4.40% × 6/12 = 1,100.00
        assertEquals(110_000L, term.expectedProfitMinor)

        val rolled = Savings.termOf(terms.copy(startDate = LocalDate.parse("2025-11-14")), 5_000_000, today)!!
        assertEquals(LocalDate.parse("2026-05-14"), rolled.start)
        val paidOut = Savings.termOf(terms.copy(startDate = LocalDate.parse("2025-11-14"), maturityChoice = MaturityChoice.PAY_OUT), 5_000_000, today)!!
        assertEquals(LocalDate.parse("2026-05-14"), paidOut.maturity)
        assertEquals(1f, paidOut.elapsed)
    }

    @Test
    fun `Hasad pays a month on the month's lowest balance, nothing under 5,000`() {
        val terms = SavingsTerms(2, SavingsKind.HASAD, "2.40")
        // 36,500 × 2.40% / 12 = 73.00
        assertEquals(7_300L, Savings.hasadProfit(terms, 3_650_000, 2))
        assertEquals(0L, Savings.hasadProfit(terms, 499_999, 2))
    }

    @Test
    fun `the lowest this month is worked back from today`() {
        val flows = mapOf(LocalDate.parse("2026-10-10") to -350_000L, LocalDate.parse("2026-10-12") to 300_000L)
        // Now 4,000,000: before the 12th 3,700,000; before the 10th 4,050,000.
        assertEquals(3_700_000L, Savings.lowestThisMonth(4_000_000, flows, today))
    }
}
