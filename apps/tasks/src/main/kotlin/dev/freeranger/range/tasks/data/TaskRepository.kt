package dev.freeranger.range.tasks.data

import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/** Keeps reminders in step with the database. Implemented with AlarmManager; faked in tests. */
interface Reminders {
    /** Schedules, moves or cancels the task's next reminder to match its current state. */
    suspend fun sync(task: Task)

    /** Cancels the reminder and removes any notification already showing. */
    fun cancel(taskId: Long)
}

/** The only way the app changes tasks, so every change also updates its reminder. */
class TaskRepository(
    private val dao: TaskDao,
    private val reminders: Reminders,
    /** Read on every call, so a time zone change is picked up without a restart. */
    private val clock: () -> Clock = { Clock.systemDefaultZone() },
) {
    val tasks: Flow<List<Task>> = dao.observeAll()

    /** Every finished day, for listing what was ticked off on each day. */
    val completions: Flow<List<Completion>> = dao.observeCompletions()

    fun task(id: Long): Flow<Task?> = dao.observe(id)

    suspend fun get(id: Long): Task? = dao.get(id)

    suspend fun add(title: String, details: String = "", schedule: Schedule? = null): Long {
        val task = Task(title = title.trim(), details = details.trim(), createdAt = clock().instant())
            .withSchedule(schedule, now())
        val id = dao.insert(task)
        reminders.sync(task.copy(id = id))
        return id
    }

    /** Saves text edits. A blank title is ignored rather than leaving an untitled task behind. */
    suspend fun editText(id: Long, title: String, details: String) {
        val task = dao.get(id) ?: return
        val updated = task.copy(title = title.trim().ifEmpty { task.title }, details = details.trim())
        if (updated != task) dao.update(updated)
    }

    /** A new schedule starts the task over, but what was already ticked off stays in the history. */
    suspend fun setSchedule(id: Long, schedule: Schedule?) = change(id, history = false) { withSchedule(schedule, now()) }

    /**
     * Ticks off once (the last tick of the day finishes it) and returns the task as it was before,
     * for Undo. Does nothing on days not yet reached. A finished day is logged [on] the day given,
     * from a past day's page, else on the day a repeat was for or the day a one-off was done.
     */
    suspend fun check(id: Long, on: LocalDate? = null): Task? {
        val before = dao.get(id) ?: return null
        change(id, on = on) { if (canCheck(today())) check(now(), clock().instant()) else this }
        return before
    }

    /**
     * Logs a gone [day] of a repeating task as done after all, when the task has already moved past
     * it: the task stays where it is, only the history changes. Returns what was logged for Undo, or
     * null if the day wasn't one of the task's or is already logged.
     *
     * Late ticks used to be logged under the day they were made. If the task's last finished day is
     * this one but was logged under another, that row is moved here instead of adding a second.
     */
    suspend fun backfill(id: Long, day: LocalDate): Backfilled? {
        val task = dao.get(id) ?: return null
        val sittingOnIt = !task.isCompleted && task.dueDate == day
        if (day >= today() || sittingOnIt || !task.wasDueOn(day, clock().zone)) return null
        val history = dao.completionsOf(id)
        if (history.any { it.day == day }) return null
        val misfiled = history.firstOrNull { task.lastDoneDay == day && it.at == task.lastDoneAt }
        if (misfiled != null) {
            dao.moveCompletion(misfiled.id, day)
            return Backfilled(misfiled.copy(day = day), movedFrom = misfiled.day)
        }
        val completion = Completion(taskId = id, day = day, at = clock().instant())
        return Backfilled(completion.copy(id = dao.insertCompletion(completion)))
    }

    /** Undoes [backfill]. */
    suspend fun unbackfill(backfilled: Backfilled) {
        val from = backfilled.movedFrom
        if (from != null) dao.moveCompletion(backfilled.completion.id, from) else dao.deleteCompletion(backfilled.completion.id)
    }

    /** Takes back one tick on the task's current day. */
    suspend fun uncheck(id: Long) = change(id) { uncheck() }

    /** Takes a task back out of Completed. */
    suspend fun reopen(id: Long) = change(id) { reopen() }

    /** Puts a task back exactly as it was, for Undo. */
    suspend fun restore(task: Task) {
        val current = dao.get(task.id) ?: return
        save(current, task)
        reminders.cancel(task.id)
        reminders.sync(task)
    }

    /**
     * The notification's Complete button: one tick. Ignored if the task has moved to another day
     * since the notification was posted, so a stale notification can't tick off the wrong day.
     */
    suspend fun completeFromReminder(id: Long, date: LocalDate) = change(id) {
        if (dueDate == date && canCheck(today())) check(now(), clock().instant()) else this
    }

    /** Re-registers the task's next reminder, after one has fired. */
    suspend fun syncReminder(id: Long) {
        dao.get(id)?.let { reminders.sync(it) }
    }

    /** Deletes the task and its history, and returns both so Undo can put them back. */
    suspend fun delete(id: Long): Deleted? {
        val task = dao.get(id) ?: return null
        val history = dao.completionsOf(id)
        dao.delete(id)
        reminders.cancel(id)
        return Deleted(task, history)
    }

    /** Undoes [delete]: the task comes back with its id, its history and its reminder. */
    suspend fun restore(deleted: Deleted) {
        dao.insert(deleted.task, deleted.history)
        reminders.sync(deleted.task)
    }

    /** Re-registers every reminder. Alarms don't survive reboots, and time changes can move them. */
    suspend fun syncAllReminders() {
        dao.openWithDueDate().forEach { reminders.sync(it) }
    }

    /** Everything, as a backup file's text. */
    suspend fun exportBackup(): String = Backup(dao.allTasks(), dao.allCompletions()).encode()

    /** Replaces everything with a backup file's contents. False, and nothing changed, if it isn't a Tasks backup. */
    suspend fun importBackup(text: String): Boolean {
        val backup = Backup.decode(text) ?: return false
        val replaced = dao.allTasks().map { it.id }
        if (runCatching { dao.replaceAll(backup) }.isFailure) return false
        replaced.forEach(reminders::cancel)
        syncAllReminders()
        return true
    }

    private suspend fun change(id: Long, history: Boolean = true, on: LocalDate? = null, transform: Task.() -> Task) {
        val task = dao.get(id) ?: return
        val updated = task.transform()
        if (updated == task) return
        if (history) save(task, updated, on) else dao.update(updated)
        // Clears a notification for a time that has just been ticked off, then sets the next reminder.
        reminders.cancel(id)
        reminders.sync(updated)
    }

    /**
     * Saves [after] and logs a newly finished day, or removes the one that was just taken back. A
     * repeat's day is logged under the day it was for, so ticking off yesterday's late still counts
     * for yesterday; [on] overrides that.
     */
    private suspend fun save(before: Task, after: Task, on: LocalDate? = null) {
        val was = before.finishedAt
        val now = after.finishedAt
        val undone = was?.takeIf { now == null || now < it }
        val finished = now?.takeIf { was == null || it > was }?.let {
            val day = on ?: after.lastDoneDay.takeIf { after.repeat != null } ?: it.atZone(clock().zone).toLocalDate()
            Completion(taskId = after.id, day = day, at = it)
        }
        dao.update(after, finished, undone)
    }

    private fun now() = LocalDateTime.now(clock())

    private fun today() = LocalDate.now(clock())
}

/** A day logged after the fact: a new row, or one moved here [movedFrom] the day a late tick was logged under. */
data class Backfilled(val completion: Completion, val movedFrom: LocalDate? = null)

/** A deleted task and its history of finished days, kept for Undo. */
data class Deleted(val task: Task, val history: List<Completion>)
