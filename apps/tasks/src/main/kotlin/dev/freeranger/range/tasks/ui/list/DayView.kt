package dev.freeranger.range.tasks.ui.list

import dev.freeranger.range.tasks.data.Completion
import dev.freeranger.range.tasks.data.Task
import dev.freeranger.range.tasks.data.days
import dev.freeranger.range.tasks.data.occursOn
import java.time.LocalDate
import java.time.LocalTime

/**
 * What the list shows for one day. Today has every section. A later day only lists what falls
 * on it, including each repeat of a repeating task, which can't be ticked off early. An earlier
 * day only lists what was ticked off on it.
 */
data class DayView(
    val overdue: List<Task> = emptyList(),
    val due: List<Task> = emptyList(),
    val anytime: List<Task> = emptyList(),
    val completed: List<Task> = emptyList(),
) {
    val hasOpenTasks: Boolean get() = overdue.isNotEmpty() || due.isNotEmpty() || anytime.isNotEmpty()
}

fun dayView(tasks: List<Task>, completions: List<Completion>, day: LocalDate, today: LocalDate): DayView {
    if (day > today) return DayView(due = tasks.filter { it.occursOn(day) }.sortedWith(timeOrder))
    if (day < today) return DayView(completed = completedOn(day, tasks, completions))

    val open = tasks.filterNot { it.isDone(today) }
    val view = DayView(
        overdue = open.filter { it.dueDate != null && it.dueDate < today }.sortedWith(dueOrder),
        due = open.filter { it.dueDate == today }.sortedWith(timeOrder),
        anytime = open.filter { it.dueDate == null }.sortedBy { it.createdAt },
    )
    // A late tick on yesterday's repeat leaves today's still open: it's listed there, not twice.
    val listed = (view.overdue + view.due + view.anytime).mapTo(HashSet()) { it.id }
    return view.copy(completed = completedOn(day, tasks, completions).filterNot { it.id in listed })
}

/** What was ticked off on [day], latest first; a task finished twice that day is listed once. */
private fun completedOn(day: LocalDate, tasks: List<Task>, completions: List<Completion>): List<Task> {
    val byId = tasks.associateBy { it.id }
    return completions.filter { it.day == day }
        .sortedByDescending { it.at }
        .distinctBy { it.taskId }
        .mapNotNull { byId[it.taskId] }
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
