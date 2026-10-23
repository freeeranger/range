package dev.freeranger.range.tasks.ui.common

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import dev.freeranger.range.tasks.R
import dev.freeranger.range.tasks.data.Repeat
import dev.freeranger.range.tasks.data.RepeatEnd
import dev.freeranger.range.tasks.data.RepeatUnit
import dev.freeranger.range.tasks.data.Schedule
import dev.freeranger.range.tasks.data.Task
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Turns dates, times and repeat rules into the short phrases shown in lists, sheets and notifications. */
class TaskFormat(context: Context) {
    private val res = context.resources

    /**
     * The app's language with the phone's region: English words in the order and clock people
     * expect where they live ("Friday 23 October" in Sweden, "Friday, October 23" in the US).
     */
    val locale: Locale = Locale.Builder()
        .setLanguage(res.getString(R.string.format_language))
        .setRegion(res.configuration.locales[0].country)
        .build()

    private val timeFormat = pattern(if (DateFormat.is24HourFormat(context)) "Hm" else "hma")
    private val weekdayFormat = pattern("EEEE")
    private val shortDateFormat = pattern("EEEdMMM")
    private val longDateFormat = pattern("EEEdMMMy")
    private val headerFormat = pattern("EEEEdMMMM")

    /** Weekdays in the order this region's calendars use. */
    val weekdays: List<DayOfWeek> = WeekFields.of(locale).firstDayOfWeek.let { first ->
        (0L until 7L).map { first.plus(it) }
    }

    fun time(time: LocalTime): String = time.format(timeFormat)

    fun header(today: LocalDate): String = today.format(headerFormat).capitalized()

    fun weekday(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, locale)

    /** "Fri", or "Fre" in Swedish: for the day strip. */
    fun weekdayShort(date: LocalDate): String = weekday(date.dayOfWeek).removeSuffix(".").capitalized()

    /** "Today", "Tomorrow", "Wednesday" within the coming week, else a short date. Capitalised, as it starts a line. */
    fun day(date: LocalDate, today: LocalDate): String = when {
        date == today -> res.getString(R.string.date_today)
        date == today.plusDays(1) -> res.getString(R.string.date_tomorrow)
        date == today.minusDays(1) -> res.getString(R.string.date_yesterday)
        date > today && date < today.plusDays(7) -> date.format(weekdayFormat)
        date.year == today.year -> date.format(shortDateFormat)
        else -> date.format(longDateFormat)
    }.capitalized()

    /** One reminder moment: "Today, 08:00", or just "Today" for all-day tasks. */
    fun moment(date: LocalDate, time: LocalTime?, today: LocalDate): String =
        listOfNotNull(day(date, today), time?.let(::time)).joinToString(", ")

    /** The day the task is on, with all its times: "Today, 08:00, 20:00". */
    fun due(task: Task, today: LocalDate): String? = task.schedule?.let { schedule(it, today) }

    /** The whole schedule, with every time of day: "Today, 08:00, 20:00". */
    fun schedule(schedule: Schedule, today: LocalDate): String =
        (listOf(day(schedule.date, today)) + schedule.times.map(::time)).joinToString(", ")

    /** "Every 2 weeks on Mon, Wed · until Fri 30 Oct". */
    fun repeat(repeat: Repeat, today: LocalDate): String {
        val every = when (repeat.unit) {
            RepeatUnit.Day -> R.plurals.repeat_days
            RepeatUnit.Week -> R.plurals.repeat_weeks
            RepeatUnit.Month -> R.plurals.repeat_months
            RepeatUnit.Year -> R.plurals.repeat_years
        }.let { res.getQuantityString(it, repeat.every, repeat.every) }

        val base = if (repeat.unit == RepeatUnit.Week && repeat.weekdays.isNotEmpty()) {
            val days = weekdays.filter { it.value in repeat.weekdays }.joinToString(", ", transform = ::weekday)
            res.getString(R.string.repeat_on_days, every, days)
        } else {
            every
        }

        val end = when (val end = repeat.end) {
            RepeatEnd.Never -> null
            is RepeatEnd.OnDate -> res.getString(R.string.repeat_until, day(end.date, today))
            is RepeatEnd.After -> res.getQuantityString(R.plurals.repeat_count, end.count, end.count)
        }
        return listOfNotNull(base, end).joinToString(" · ")
    }

    // Swedish writes weekdays and months in lowercase, even at the start of a line.
    private fun String.capitalized() = replaceFirstChar { it.titlecase(locale) }

    private fun pattern(skeleton: String): DateTimeFormatter =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
}

/** A [TaskFormat] that refreshes when the phone's language, region or clock setting changes. */
@Composable
fun rememberTaskFormat(): TaskFormat {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { TaskFormat(context) }
}
