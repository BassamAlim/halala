package bassamalim.halala.core.reminders

import bassamalim.halala.core.models.ReminderMode
import bassamalim.halala.core.models.ReviewSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime

class ReviewRemindersTest {

    // A Wednesday, 21:00 in Riyadh.
    private val now = ZonedDateTime.parse("2026-09-30T21:00:00+03:00[Asia/Riyadh]")

    private fun next(mode: ReminderMode, day: DayOfWeek = DayOfWeek.SATURDAY, time: String = "20:00") =
        ReviewReminders.nextAt(now, ReviewSchedule(mode, day, LocalTime.parse(time)))?.toLocalDateTime().toString()

    @Test
    fun `the next reminder is always ahead of now`() {
        assertNull(ReviewReminders.nextAt(now, ReviewSchedule(ReminderMode.OFF)))
        assertEquals("2026-10-01T20:00", next(ReminderMode.DAILY))
        assertEquals("2026-09-30T22:30", next(ReminderMode.DAILY, time = "22:30"))
        assertEquals("2026-10-03T20:00", next(ReminderMode.WEEKLY))
        // Today's weekday with the time already past: a week from now, not today.
        assertEquals("2026-10-07T20:00", next(ReminderMode.WEEKLY, DayOfWeek.WEDNESDAY))
        assertEquals("2026-09-30T22:30", next(ReminderMode.WEEKLY, DayOfWeek.WEDNESDAY, "22:30"))
    }
}
