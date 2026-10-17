package bassamalim.halala.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import bassamalim.halala.core.data.dataSources.room.entities.RetirementPlan
import java.math.BigDecimal
import java.time.LocalDate

class PlannerTest {

    @Test
    fun `no return is just what you put in`() {
        val result = Planner.compound(100_000, 10_000, BigDecimal.ZERO, 12, 2)
        assertEquals(100_000 + 24 * 10_000L, result.finalMinor)
        assertEquals(0L, result.returnsMinor)
    }

    @Test
    fun `a yearly rate compounds once a year`() {
        // 1,000.00 at 10% for 2 years: 1,210.00.
        assertEquals(121_000L, Planner.compound(100_000, 0, BigDecimal(10), 1, 2).finalMinor)
    }

    @Test
    fun `retirement projects the pot, its worth today, the income it supports and the gap`() {
        val result = Planner.retirement(
            RetirementInputs(ageNow = 32, retireAt = 55, startMinor = 26_295_000, monthlyMinor = 400_000,
                returnPercent = BigDecimal(6), inflationPercent = BigDecimal("2.5"), wantedMonthlyMinor = 800_000)
        )!!
        // The board's example compounded monthly: about 3.41M, 1.93M today, 6,443 a month.
        assertEquals(341_061_837L, result.potMinor)
        assertEquals(193_278_801L, result.potTodayMinor)
        assertEquals(644_263L, result.supportsMonthlyMinor)
        assertEquals(800_000 - result.supportsMonthlyMinor, result.shortMonthlyMinor)
        assertTrue(result.extraMonthlyMinor > 0)
        assertEquals(24, result.curve.size)
        assertNull(Planner.retirement(RetirementInputs(55, 55, 0, 0, BigDecimal.ONE, BigDecimal.ONE, 0)))
    }

    @Test
    fun `saving the extra closes the gap`() {
        val base = RetirementInputs(30, 60, 0, 100_000, BigDecimal(5), BigDecimal(2), 500_000)
        val first = Planner.retirement(base)!!
        val closed = Planner.retirement(base.copy(monthlyMinor = base.monthlyMinor + first.extraMonthlyMinor))!!
        assertTrue(closed.shortMonthlyMinor <= 1)
    }

    @Test
    fun `the needed saving a month reaches the pot the income needs`() {
        val base = RetirementInputs(30, 60, 1_000_000, 0, BigDecimal(5), BigDecimal(2), 500_000)
        val first = Planner.retirement(base)!!
        val there = Planner.retirement(base.copy(monthlyMinor = first.neededMonthlyMinor))!!
        assertTrue(there.shortMonthlyMinor <= 1)
        assertEquals(first.neededMonthlyMinor, first.extraMonthlyMinor)
        // A pot already big enough needs nothing more.
        assertEquals(0L, Planner.retirement(base.copy(startMinor = first.neededMinor))!!.neededMonthlyMinor)
    }

    @Test
    fun `a plan's age is this year less the year you were born`() {
        val plan = RetirementPlan(goalId = 1, birthYear = 1994, retireAt = 55, wantedMinor = 800_000, returnPercent = "6", inflationPercent = "2.5", currency = "SAR")
        val inputs = Planner.inputsOf(plan, 26_295_000, 400_000, LocalDate.parse("2026-10-17"))!!
        assertEquals(32, inputs.ageNow)
        assertEquals(BigDecimal("2.5"), inputs.inflationPercent)
        assertEquals(LocalDate.parse("2049-01-01"), Planner.retireOn(plan))
        assertNull(Planner.inputsOf(plan, 0, 0, LocalDate.parse("2049-06-01")))
    }
}
