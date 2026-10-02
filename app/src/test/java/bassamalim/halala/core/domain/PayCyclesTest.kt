package bassamalim.halala.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PayCyclesTest {

    private fun salary(day: String, minor: Long = 1_800_000) = SalaryCredit(LocalDate.parse(day), minor)

    private val salaries = listOf(salary("2026-07-27"), salary("2026-08-26"), salary("2026-08-28", 50_000), salary("2026-09-27"))

    @Test
    fun `a cycle runs from the last salary to a month after it`() {
        val cycle = PayCycles.current(salaries, LocalDate.parse("2026-10-14"))
        assertTrue(cycle.fromSalary)
        assertEquals(LocalDate.parse("2026-09-27"), cycle.start)
        assertEquals(LocalDate.parse("2026-10-27"), cycle.end)
        assertEquals(13L, cycle.daysLeft(LocalDate.parse("2026-10-14")))
        assertEquals(1_800_000L, cycle.salaryMinor)
    }

    @Test
    fun `a late salary keeps the cycle open, and a stale one falls back to the month`() {
        assertEquals(LocalDate.parse("2026-10-30"), PayCycles.current(salaries, LocalDate.parse("2026-10-29")).end)
        val month = PayCycles.current(salaries, LocalDate.parse("2026-12-10"))
        assertFalse(month.fromSalary)
        assertEquals(LocalDate.parse("2026-12-01"), month.start)
    }

    @Test
    fun `an allowance two days after the salary doesn't start a cycle`() {
        assertEquals(listOf("2026-07-27", "2026-08-26", "2026-09-27"), PayCycles.payDays(salaries).map { it.date.toString() })
    }

    @Test
    fun `previous cycles run between pay days, newest first`() {
        val current = PayCycles.current(salaries, LocalDate.parse("2026-10-14"))
        assertEquals(
            listOf("2026-08-26" to "2026-09-27", "2026-07-27" to "2026-08-26"),
            PayCycles.previous(salaries, current, 3).map { it.start.toString() to it.end.toString() }
        )
    }

    @Test
    fun `without salary there is the calendar month`() {
        val cycle = PayCycles.current(emptyList(), LocalDate.parse("2026-02-10"))
        assertEquals(LocalDate.parse("2026-02-01"), cycle.start)
        assertEquals(LocalDate.parse("2026-03-01"), cycle.end)
    }
}
