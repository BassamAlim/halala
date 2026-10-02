package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.SavingsGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class GoalsTest {

    private val today = LocalDate.parse("2026-10-14")

    @Test
    fun `what is left over the months to the target month, rounded up`() {
        val goal = SavingsGoal(uid = "g", name = "Fund", targetMinor = 6_000_000, currency = "SAR", targetDate = LocalDate.parse("2027-03-01"), accountIds = listOf(1), createdAt = Instant.EPOCH)
        val state = Goals.stateOf(goal, 4_200_000, 1_020_000, today)
        // Oct to Mar is six months.
        assertEquals(300_000L, state.neededMonthlyMinor)
        assertEquals(340_000L, state.averageMonthlyMinor)
        assertEquals(1, Goals.perMonth(1, 6))
        assertEquals(0, Goals.perMonth(-5, 6))
        assertNull(Goals.stateOf(goal.copy(targetDate = null), 0, 0, today).neededMonthlyMinor)
    }
}
