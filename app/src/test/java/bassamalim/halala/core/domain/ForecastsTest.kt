package bassamalim.halala.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ForecastsTest {

    private val today = LocalDate.parse("2026-10-14")
    private val cycle = PayCycle(LocalDate.parse("2026-09-27"), LocalDate.parse("2026-10-27"), fromSalary = true, salaryMinor = 1_800_000)

    private fun inputs(rates: List<Long> = listOf(20_000, 25_000, 30_000), scheduled: List<Scheduled> = emptyList()) =
        ForecastInputs(today, 1_000_000, cycle, rates, scheduled, 1_800_000)

    @Test
    fun `the end of the cycle is the balance less what is scheduled and the median spending`() {
        val estimate = Forecasts.endOfCycle(inputs(scheduled = listOf(Scheduled(LocalDate.parse("2026-10-20"), "Netflix", 5_600))))!!
        // 13 days left at 250.00 a day, and Netflix.
        assertEquals(1_000_000 - 5_600 - 13 * 25_000, estimate.midMinor)
        assertEquals(1_000_000 - 5_600 - 13 * 30_000, estimate.lowMinor)
        assertEquals(1_000_000 - 5_600 - 13 * 20_000, estimate.highMinor)
        assertNull(Forecasts.endOfCycle(inputs(rates = emptyList())))
    }

    @Test
    fun `affording it reports the lowest balance and its day, salary counted`() {
        val result = Forecasts.afford(inputs(), 600_000, LocalDate.parse("2026-10-20"))
        // Lowest is the day before the salary on 27 Oct.
        assertEquals(LocalDate.parse("2026-10-26"), result.lowestOn)
        assertEquals(1_000_000 - 12 * 25_000 - 600_000, result.lowestMinor)
        assertTrue(result.affordable)
        assertFalse(Forecasts.afford(inputs(), 900_000, today).affordable)
    }

    @Test
    fun `months ahead leave salary less scheduled and a month of spending`() {
        val months = Forecasts.monthsAhead(inputs(scheduled = listOf(Scheduled(LocalDate.parse("2026-11-01"), "Rent", 350_000))), 2)
        assertEquals(YearMonth.of(2026, 11), months[0].month)
        assertEquals(1_800_000 - 350_000 - 30 * 25_000, months[0].leftMinor)
        assertEquals(1_800_000 - 31 * 25_000, months[1].leftMinor)
        assertEquals("Rent", months[0].biggest.single().name)
    }

    @Test
    fun `a daily rate rounds half up`() {
        assertEquals(3, Forecasts.rate(10, 4))
    }
}
