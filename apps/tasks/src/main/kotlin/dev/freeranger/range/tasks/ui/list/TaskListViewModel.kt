package dev.freeranger.range.tasks.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.freeranger.range.tasks.data.Backfilled
import dev.freeranger.range.tasks.data.Completion
import dev.freeranger.range.tasks.data.Deleted
import dev.freeranger.range.tasks.data.Schedule
import dev.freeranger.range.tasks.data.Task
import dev.freeranger.range.tasks.data.TaskRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TaskListState(
    val tasks: List<Task> = emptyList(),
    /** Every finished day, so each day can list what was ticked off on it. */
    val completions: List<Completion> = emptyList(),
    /** False until the database has answered, so the empty state doesn't flash on launch. */
    val loaded: Boolean = false,
)

/** A tick that just happened, with the task as it was before, or the day logged after the fact, so it can be undone. */
data class Ticked(val before: Task, val after: Task, val backfilled: Backfilled? = null)

class TaskListViewModel(private val repository: TaskRepository) : ViewModel() {
    val state: StateFlow<TaskListState> = combine(repository.tasks, repository.completions) { tasks, completions ->
        TaskListState(tasks = tasks, completions = completions, loaded = true)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskListState())

    private val ticks = Channel<Ticked>(Channel.CONFLATED)

    /** Each tick, for the Undo snackbar. */
    val ticked: Flow<Ticked> = ticks.receiveAsFlow()

    fun add(title: String, details: String, schedule: Schedule?) {
        viewModelScope.launch { repository.add(title, details, schedule) }
    }

    /**
     * Ticks off once, or from a past [day]'s page, for that day: the task's own tick if it's still
     * on that day, otherwise the day is logged as done after all.
     */
    fun check(id: Long, day: LocalDate? = null) {
        viewModelScope.launch {
            val task = repository.get(id) ?: return@launch
            if (day != null && (task.isCompleted || task.dueDate != day)) {
                repository.backfill(id, day)?.let { ticks.send(Ticked(task, task, it)) }
                return@launch
            }
            val before = repository.check(id, on = day) ?: return@launch
            val after = repository.get(id) ?: return@launch
            if (after != before) ticks.send(Ticked(before, after))
        }
    }

    fun undo(ticked: Ticked) {
        viewModelScope.launch {
            ticked.backfilled?.let { repository.unbackfill(it) } ?: repository.restore(ticked.before)
        }
    }

    fun reopen(id: Long) {
        viewModelScope.launch { repository.reopen(id) }
    }

    private val deletions = Channel<Deleted>(Channel.CONFLATED)

    /** Each deleted task, for the Undo snackbar. */
    val deleted: Flow<Deleted> = deletions.receiveAsFlow()

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id)?.let { deletions.send(it) } }
    }

    fun undoDelete(deleted: Deleted) {
        viewModelScope.launch { repository.restore(deleted) }
    }
}
