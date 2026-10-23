package dev.freeranger.range.tasks.data

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * A task. A repeating task is a single row that moves from day to day: [dueDate] is the day it
 * is currently on. A task with several [times] is done that many times a day; [doneCount] counts
 * how many are ticked off. The times themselves are only when reminders fire.
 */
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val details: String = "",
    /** The day the task is due, or, for a repeating task, the day it is currently on. Null for undated tasks. */
    val dueDate: LocalDate? = null,
    /** Reminder times on [dueDate], sorted. Empty for all-day tasks. */
    val times: List<LocalTime> = emptyList(),
    /** How many of the day's [steps] are ticked off. */
    @ColumnInfo(defaultValue = "0") val doneCount: Int = 0,
    val repeat: Repeat? = null,
    /** 1-based number of the day a repeating task is on, for repeats that end after a number of times. */
    val occurrence: Int = 1,
    /** Set when the task is finished for good: a one-off task ticked off, or a repeat's last day. */
    val completedAt: Instant? = null,
    /** When a repeating task last had a whole day ticked off, so "done for today" can be shown and undone. */
    val lastDoneAt: Instant? = null,
    /** The day that was ticked off at [lastDoneAt]. Undo moves the task back to it. */
    val lastDoneDay: LocalDate? = null,
    val createdAt: Instant,
) {
    val isCompleted: Boolean get() = completedAt != null

    val schedule: Schedule? get() = dueDate?.let { Schedule(it, times, repeat) }

    /** How many times a day the task is done: once per reminder time, and at least once. */
    val steps: Int get() = times.size.coerceAtLeast(1)

    /** The reminder time for the next tick, used to order the list. */
    val nextTime: LocalTime? get() = times.getOrNull(doneCount) ?: times.lastOrNull()

    /**
     * Whether the task can be ticked off today. A repeating task can't be ticked off before
     * the day it is on: finishing today's doesn't let you get ahead on tomorrow's.
     */
    fun canCheck(today: LocalDate): Boolean =
        !isCompleted && !(repeat != null && dueDate != null && dueDate > today)

    /** Finished for good, or a repeating task whose whole day was ticked off today. Shown under Completed. */
    fun isDone(today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (isCompleted) return true
        val doneToday = lastDoneAt?.atZone(zone)?.toLocalDate() == today
        return repeat != null && doneToday && dueDate != null && dueDate > today
    }

    /**
     * Due day passed, or the day's last time passed with ticks still missing. Earlier times
     * don't count: they say when to be reminded, not a deadline.
     */
    fun isOverdue(now: LocalDateTime): Boolean {
        if (isCompleted || dueDate == null) return false
        val today = now.toLocalDate()
        val last = times.lastOrNull()
        return dueDate < today || (dueDate == today && last != null && last < now.toLocalTime())
    }

    /**
     * Whether the reminder at [time] should still fire. The reminder at the n-th time fires only
     * while fewer than n ticks are done, so ticking off early silences the reminders it covers.
     */
    fun wantsReminder(time: LocalTime?): Boolean {
        if (isCompleted) return false
        if (time == null) return doneCount == 0
        val index = times.indexOf(time)
        return index >= 0 && doneCount <= index
    }

    /** The next moment to remind about, with the time it is for (null for all-day tasks), or null if none is left. */
    fun nextReminder(now: LocalDateTime): Pair<LocalDateTime, LocalTime?>? {
        val day = dueDate ?: return null
        if (times.isEmpty()) {
            val at = day.atTime(ALL_DAY_REMINDER)
            return if (wantsReminder(null) && at > now) at to null else null
        }
        val time = times.firstOrNull { wantsReminder(it) && day.atTime(it) > now } ?: return null
        return day.atTime(time) to time
    }

    companion object {
        /** All-day tasks remind in the morning, as Google Tasks does. */
        val ALL_DAY_REMINDER: LocalTime = LocalTime.of(9, 0)
    }
}

/** What the user picks in the schedule sheet: a start date, optional times, optional repeat. */
data class Schedule(
    val date: LocalDate,
    val times: List<LocalTime> = emptyList(),
    val repeat: Repeat? = null,
)
