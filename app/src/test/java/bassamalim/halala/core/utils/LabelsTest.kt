package bassamalim.halala.core.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class LabelsTest {

    private val today = LocalDate.of(2026, 9, 30)

    @Test
    fun `recent days are named, others dated the way the design writes them`() {
        assertEquals(DayLabel.Today, dayLabel(today, today))
        assertEquals(DayLabel.Yesterday, dayLabel(today.minusDays(1), today))
        assertEquals(DayLabel.On("Sun 27 Sep"), dayLabel(LocalDate.of(2026, 9, 27), today))
        assertEquals(DayLabel.On("Sat 27 Sep 2025"), dayLabel(LocalDate.of(2025, 9, 27), today))
    }

    @Test
    fun `times are 24-hour`() {
        assertEquals("21:14", timeLabel(LocalTime.of(21, 14, 59)))
        assertEquals("07:05", timeLabel(LocalTime.of(7, 5)))
    }

    @Test
    fun `accounts are named bank then nickname`() {
        assertEquals("Al Rajhi – Salary", accountLabel("Al Rajhi", "Salary"))
        assertEquals("Cash", accountLabel(null, "Cash"))
        assertEquals("D360", accountLabel("D360", "D360"))
        assertEquals("D360", accountLabel("D360", " "))
    }

    @Test
    fun `avatars take the first letter or digit`() {
        assertEquals("P", initialOf("panda"))
        assertEquals("ه", initialOf("هللة"))
        assertEquals("7", initialOf("  7-Eleven"))
        assertEquals("·", initialOf(""))
    }

    @Test
    fun `last four digits are shown masked`() {
        assertEquals("••4821", maskedLast4("4821"))
    }
}
