package dev.freeranger.range.tasks.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.min

// Bounds the searches below. 20,000 days is about 55 years, past any sensible repeat.
private const val MAX_DAYS = 20_000

/** Whether [date] is one of this rule's days. */
fun Repeat.matches(date: LocalDate): Boolean {
    if (date < anchor) return false
    return when (unit) {
        RepeatUnit.Day -> ChronoUnit.DAYS.between(anchor, date) % every == 0L
        RepeatUnit.Week -> {
            val days = weekdays.ifEmpty { setOf(anchor.dayOfWeek.value) }
            val weeks = ChronoUnit.WEEKS.between(anchor.startOfWeek(), date.startOfWeek())
            date.dayOfWeek.value in days && weeks % every == 0L
        }
        RepeatUnit.Month -> {
            val months = ChronoUnit.MONTHS.between(anchor.withDayOfMonth(1), date.withDayOfMonth(1))
            months % every == 0L && date.dayOfMonth == min(anchor.dayOfMonth, date.lengthOfMonth())
        }
        RepeatUnit.Year -> {
            val years = date.year - anchor.year
            years % every == 0 && date.month == anchor.month &&
                date.dayOfMonth == min(anchor.dayOfMonth, date.lengthOfMonth())
        }
    }
}

/** The first day of this rule strictly after [after], or null if there is none within range. */
fun Repeat.nextDate(after: LocalDate): LocalDate? {
    var date = after.plusDays(1)
    repeat(MAX_DAYS) {
        if (matches(date)) return date
        date = date.plusDays(1)
    }
    return null
}

/** The first day on or after [from] that the rule covers. */
fun Repeat.firstDate(from: LocalDate): LocalDate? = if (matches(from)) from else nextDate(from)

/**
 * Applies a new [schedule]. A repeating task starts on the first of its days that still has
 * something ahead, so "8:00 every day" created in the evening starts tomorrow. A one-off task
 * keeps the chosen day even if it has passed, since the user asked for it.
 */
fun Task.withSchedule(schedule: Schedule?, now: LocalDateTime): Task {
    val reset = copy(doneCount = 0, occurrence = 1, lastDoneAt = null, lastDoneDay = null)
    if (schedule == null) return reset.copy(dueDate = null, times = emptyList(), repeat = null)

    val times = schedule.times.distinct().sorted()
    val repeat = schedule.repeat?.copy(anchor = schedule.date) ?: return reset.copy(dueDate = schedule.date, times = times, repeat = null)

    val today = now.toLocalDate()
    fun hasSomethingAhead(day: LocalDate) =
        day > today || (day == today && (times.isEmpty() || times.any { it > now.toLocalTime() }))

    var day = repeat.firstDate(schedule.date) ?: schedule.date
    var index = 1
    while (!hasSomethingAhead(day)) {
        val next = repeat.nextDate(day)?.takeIf { repeat.end.allows(it, index + 1) } ?: break
        day = next
        index++
    }
    return reset.copy(dueDate = day, times = times, repeat = repeat)
}

/** Ticks off once. The last tick of the day finishes it. */
fun Task.check(now: LocalDateTime, at: Instant): Task =
    if (doneCount + 1 >= steps) finishDay(now, at) else copy(doneCount = doneCount + 1)

/** Takes back one tick on the current day. */
fun Task.uncheck(): Task = if (doneCount > 0 && !isCompleted) copy(doneCount = doneCount - 1) else this

/**
 * Ticks off the whole current day. A one-off task is then completed; a repeating task moves to
 * its next day, skipping days already in the past so a late tick doesn't leave a backlog.
 */
fun Task.finishDay(now: LocalDateTime, at: Instant): Task {
    val repeat = repeat
    val day = dueDate
    if (repeat == null || day == null) return copy(doneCount = steps, completedAt = at)

    val today = now.toLocalDate()
    var next: LocalDate = day
    var index = occurrence
    while (true) {
        next = repeat.nextDate(next) ?: break
        index++
        if (!repeat.end.allows(next, index)) break
        if (next >= today) {
            return copy(dueDate = next, doneCount = 0, occurrence = index, lastDoneAt = at, lastDoneDay = day)
        }
    }
    // The series is over.
    return copy(doneCount = steps, completedAt = at, lastDoneAt = at, lastDoneDay = day)
}

/** Undoes the last finished day, for tasks shown under Completed: takes back that day's last tick. */
fun Task.reopen(): Task {
    if (isCompleted) return copy(completedAt = null, doneCount = steps - 1, lastDoneAt = null, lastDoneDay = null)
    val day = lastDoneDay ?: return this
    return copy(
        dueDate = day,
        doneCount = steps - 1,
        occurrence = (occurrence - 1).coerceAtLeast(1),
        lastDoneAt = null,
        lastDoneDay = null,
    )
}

/**
 * The days this task falls on, from the day it is currently on up to [until]: just its due day
 * for a one-off task, every day of the series for a repeating one. Used to show tasks on future days.
 */
fun Task.days(until: LocalDate): List<LocalDate> {
    val start = dueDate?.takeUnless { isCompleted } ?: return emptyList()
    val repeat = repeat ?: return if (start <= until) listOf(start) else emptyList()
    val days = mutableListOf<LocalDate>()
    var day = start
    var index = occurrence
    while (day <= until) {
        days += day
        day = repeat.nextDate(day) ?: break
        index++
        if (!repeat.end.allows(day, index)) break
    }
    return days
}

fun Task.occursOn(day: LocalDate): Boolean = day in days(until = day)

/**
 * Whether [day], already gone, was one of the task's days: a one-off task's due day while it's
 * still open, or a repeat's day from when the task was made up to the day it's on now (its last
 * day, once the series is over). Whether it was ticked off then is the history's to say.
 */
fun Task.wasDueOn(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean {
    val current = dueDate ?: return false
    val repeat = repeat ?: return !isCompleted && day == current
    val last = if (isCompleted) lastDoneDay ?: return false else current
    if (day > last || !repeat.matches(day)) return false
    // A task made in the evening for mornings starts the next day, as withSchedule has it.
    val created = createdAt.atZone(zone)
    val firstDay = created.toLocalDate()
    return day > firstDay || (day == firstDay && (times.isEmpty() || times.any { it > created.toLocalTime() }))
}

private fun RepeatEnd.allows(day: LocalDate, index: Int): Boolean = when (this) {
    RepeatEnd.Never -> true
    is RepeatEnd.OnDate -> day <= date
    is RepeatEnd.After -> index <= count
}

private fun LocalDate.startOfWeek(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
