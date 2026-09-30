package bassamalim.halala.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetStateTest {

    @Test
    fun `under 80 percent is on track`() {
        assertEquals(BudgetState.OK, BudgetState.of(spentMinor = 0, budgetMinor = 900_000))
        assertEquals(BudgetState.OK, BudgetState.of(spentMinor = 624_000, budgetMinor = 900_000))
        assertEquals(BudgetState.OK, BudgetState.of(spentMinor = 719_999, budgetMinor = 900_000))
    }

    @Test
    fun `80 to 100 percent, both ends included, is spending fast`() {
        assertEquals(BudgetState.WARN, BudgetState.of(spentMinor = 720_000, budgetMinor = 900_000))
        assertEquals(BudgetState.WARN, BudgetState.of(spentMinor = 779_000, budgetMinor = 900_000))
        assertEquals(BudgetState.WARN, BudgetState.of(spentMinor = 900_000, budgetMinor = 900_000))
    }

    @Test
    fun `a halala over is over budget`() {
        assertEquals(BudgetState.OVER, BudgetState.of(spentMinor = 900_001, budgetMinor = 900_000))
        assertEquals(BudgetState.OVER, BudgetState.of(spentMinor = 972_000, budgetMinor = 900_000))
    }

    @Test
    fun `huge amounts compare exactly rather than overflowing`() {
        assertEquals(BudgetState.WARN, BudgetState.of(spentMinor = Long.MAX_VALUE, budgetMinor = Long.MAX_VALUE))
        assertEquals(BudgetState.OK, BudgetState.of(spentMinor = Long.MAX_VALUE / 2, budgetMinor = Long.MAX_VALUE))
    }

    @Test
    fun `no budget is over as soon as anything is spent`() {
        assertEquals(BudgetState.OK, BudgetState.of(spentMinor = 0, budgetMinor = 0))
        assertEquals(BudgetState.OVER, BudgetState.of(spentMinor = 1, budgetMinor = 0))
    }

    @Test
    fun `the bar fills to the fraction spent and stops at full`() {
        assertEquals(0.69f, BudgetState.progress(621_000, 900_000), 0.001f)
        assertEquals(1f, BudgetState.progress(972_000, 900_000), 0f)
        assertEquals(0f, BudgetState.progress(0, 0), 0f)
    }
}
