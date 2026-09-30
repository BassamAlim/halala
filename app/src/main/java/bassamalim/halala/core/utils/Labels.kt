package bassamalim.halala.core.utils

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The words a date is shown as. Today and Yesterday are resolved to strings by the screen (they
 * are translatable copy); anything else is already formatted.
 */
sealed interface DayLabel {
    data object Today : DayLabel
    data object Yesterday : DayLabel
    data class On(val text: String) : DayLabel
}

/** "Today", "Yesterday", "Sat 27 Sep", or "Sat 27 Sep 2025" outside the current year. */
fun dayLabel(date: LocalDate, today: LocalDate): DayLabel = when (date) {
    today -> DayLabel.Today
    today.minusDays(1) -> DayLabel.Yesterday
    else -> DayLabel.On(
        date.format(if (date.year == today.year) DAY_FORMAT else DAY_WITH_YEAR_FORMAT)
    )
}

/** "Mon 29 Sep", with the year when it isn't this one. For headers that always name the day. */
fun dateLabel(date: LocalDate, today: LocalDate): String =
    date.format(if (date.year == today.year) DAY_FORMAT else DAY_WITH_YEAR_FORMAT)

/** 24-hour, as the design writes it: "21:14". */
fun timeLabel(time: LocalTime): String = time.format(TIME_FORMAT)

/** "Al Rajhi – Salary", the way the design names an account; just "Cash" for the wallet. */
fun accountLabel(institutionName: String?, nickname: String): String = when {
    institutionName.isNullOrBlank() -> nickname
    nickname.isBlank() || nickname.equals(institutionName, ignoreCase = true) -> institutionName
    else -> "$institutionName – $nickname"
}

/** "••4821". */
fun maskedLast4(last4: String): String = "••$last4"

/** The letter in an avatar: the first letter or digit of the title, else a neutral dot. */
fun initialOf(title: String): String =
    title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "·"

/**
 * English month and day names in v1, whatever the device language: the UI is English, and the
 * design's short month is "Sep" (Locale.UK now says "Sept").
 */
private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
private val DAY_WITH_YEAR_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.US)
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
