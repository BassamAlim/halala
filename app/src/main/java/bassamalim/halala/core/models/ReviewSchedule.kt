package bassamalim.halala.core.models

import java.time.DayOfWeek
import java.time.LocalTime

/** How often the review reminder asks. */
enum class ReminderMode { OFF, DAILY, WEEKLY }

/** When the review reminder asks: every day or on one [day] of the week, at [time]. */
data class ReviewSchedule(
    val mode: ReminderMode = ReminderMode.OFF,
    val day: DayOfWeek = DayOfWeek.SATURDAY,
    val time: LocalTime = LocalTime.of(20, 0)
)
