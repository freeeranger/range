package dev.freeranger.range.tasks.ui.list

import dev.freeranger.range.tasks.data.Task
import dev.freeranger.range.tasks.data.days
import dev.freeranger.range.tasks.data.occursOn
import java.time.LocalDate
import java.time.LocalTime

/**
 * What the list shows for one day. Today has every section; another day only lists what
 * falls on it, including each repeat of a repeating task, which can't be ticked off early.
 */
data class DayView(
    val overdue: List<Task> = emptyList(),
    val due: List<Task> = emptyList(),
    val anytime: List<Task> = emptyList(),
    val completed: List<Task> = emptyList(),
) {
    val hasOpenTasks: Boolean get() = overdue.isNotEmpty() || due.isNotEmpty() || anytime.isNotEmpty()
}

fun dayView(tasks: List<Task>, day: LocalDate, today: LocalDate): DayView {
    if (day != today) {
        return DayView(due = tasks.filter { it.occursOn(day) }.sortedWith(timeOrder))
    }
    val (done, open) = tasks.partition { it.isDone(today) }
    return DayView(
        overdue = open.filter { it.dueDate != null && it.dueDate < today }.sortedWith(dueOrder),
        due = open.filter { it.dueDate == today }.sortedWith(timeOrder),
        anytime = open.filter { it.dueDate == null }.sortedBy { it.createdAt },
        completed = done.sortedByDescending { it.completedAt ?: it.lastDoneAt },
    )
}

/** Days between [today] and [until] that have something on them, for the dots in the day strip. */
fun markedDays(tasks: List<Task>, today: LocalDate, until: LocalDate): Set<LocalDate> = buildSet {
    tasks.forEach { task ->
        when {
            task.isCompleted -> Unit
            // Undated and overdue tasks are on today's list.
            task.dueDate == null || task.dueDate < today -> if (!task.isDone(today)) add(today)
            else -> task.days(until).filterTo(this) { it >= today && !(it == today && task.isDone(today)) }
        }
    }
}

/** Soonest first; all-day before timed on the same day; oldest first after that. */
private val dueOrder = compareBy<Task, LocalDate?>(nullsLast()) { it.dueDate }
    .thenBy(nullsFirst()) { it.nextTime }
    .thenBy { it.createdAt }

/** Within one day: all-day first, then by the next reminder time. */
private val timeOrder = compareBy<Task, LocalTime?>(nullsFirst()) { it.nextTime }
    .thenBy { it.createdAt }
