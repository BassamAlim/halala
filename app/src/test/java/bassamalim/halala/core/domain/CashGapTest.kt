package bassamalim.halala.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CashGapTest {

    @Test
    fun `less in the wallet than recorded was spent`() {
        assertEquals(CashGap.Spent(6_200), CashGap.of(recordedMinor = 50_000, countedMinor = 43_800))
    }

    @Test
    fun `more in the wallet than recorded was found`() {
        assertEquals(CashGap.Found(2_000), CashGap.of(recordedMinor = 50_000, countedMinor = 52_000))
    }

    @Test
    fun `a match records nothing`() {
        assertEquals(CashGap.None, CashGap.of(recordedMinor = 50_000, countedMinor = 50_000))
    }

    @Test
    fun `a wallet the ledger thinks is overdrawn still reconciles`() {
        assertEquals(CashGap.Found(15_000), CashGap.of(recordedMinor = -5_000, countedMinor = 10_000))
    }
}
