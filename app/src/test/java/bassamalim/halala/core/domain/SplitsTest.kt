package bassamalim.halala.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitsTest {

    @Test
    fun `equal shares round down, and the remainder stays yours`() {
        assertEquals(3_333, Splits.equalShare(10_000, 2))
        assertEquals(3_334, Splits.yours(10_000, listOf(3_333, 3_333)))
    }

    @Test
    fun `a split needs someone, every share, and no more than you paid`() {
        assertEquals(setOf(SplitProblem.NoOne), Splits.validate(100, emptyMap()))
        assertEquals(setOf(SplitProblem.ShareMissing), Splits.validate(100, mapOf(1L to null)))
        assertEquals(setOf(SplitProblem.TooMuch), Splits.validate(100, mapOf(1L to 60, 2L to 50)))
        assertEquals(emptySet<SplitProblem>(), Splits.validate(100, mapOf(1L to 50, 2L to 50)))
    }
}
