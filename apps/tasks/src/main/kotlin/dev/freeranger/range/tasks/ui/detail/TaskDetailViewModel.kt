package dev.freeranger.range.tasks.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.freeranger.range.tasks.data.Schedule
import dev.freeranger.range.tasks.data.Task
import dev.freeranger.range.tasks.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class TaskDetailViewModel(
    private val id: Long,
    private val repository: TaskRepository,
    /** Outlives this screen, for changes made right before leaving it. */
    private val appScope: CoroutineScope,
) : ViewModel() {
    val task: StateFlow<Task?> = repository.task(id).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Saves an edit. The schedule is only rewritten if it was touched, so saving a new title doesn't reset progress. */
    fun save(title: String, details: String, schedule: Schedule?, scheduleChanged: Boolean) {
        appScope.launch {
            repository.editText(id, title, details)
            if (scheduleChanged) repository.setSchedule(id, schedule)
        }
    }

    /** Ticks off once, like the list's checkbox. [onDayDone] runs if that finished the task for today. */
    fun check(onDayDone: () -> Unit) {
        viewModelScope.launch {
            repository.check(id)
            val after = repository.get(id) ?: return@launch
            if (after.isDone(LocalDate.now())) onDayDone()
        }
    }

    /** Takes back one tick. */
    fun uncheck() {
        viewModelScope.launch { repository.uncheck(id) }
    }

    fun reopen() {
        viewModelScope.launch { repository.reopen(id) }
    }

    fun delete() {
        appScope.launch { repository.delete(id) }
    }
}
