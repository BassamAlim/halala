package bassamalim.halala.core.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockRuleTest {

    private val grace = LockManager.DEFAULT_GRACE_MILLIS

    @Test
    fun `the default grace is a minute`() {
        assertEquals(60_000L, grace)
    }

    @Test
    fun `a quick hop to another app and back does not re-prompt`() {
        assertFalse(shouldLock(backgroundedAt = 0, now = 10_000, graceMillis = grace))
        assertFalse(shouldLock(backgroundedAt = 0, now = grace - 1, graceMillis = grace))
    }

    @Test
    fun `a minute away is a new session`() {
        assertTrue(shouldLock(backgroundedAt = 0, now = grace, graceMillis = grace))
        assertTrue(shouldLock(backgroundedAt = 0, now = 5 * 60_000, graceMillis = grace))
    }

    @Test
    fun `a session that was never unlocked and backgrounded has nothing to re-lock`() {
        assertFalse(shouldLock(backgroundedAt = null, now = 10 * 60_000, graceMillis = grace))
    }

    @Test
    fun `a zero grace locks on every return`() {
        assertTrue(shouldLock(backgroundedAt = 1_000, now = 1_000, graceMillis = 0))
    }
}
