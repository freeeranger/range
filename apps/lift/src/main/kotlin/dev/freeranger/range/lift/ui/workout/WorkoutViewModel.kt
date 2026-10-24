package dev.freeranger.range.lift.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.freeranger.range.lift.data.LiftRepository
import dev.freeranger.range.lift.data.LoggedExercise
import dev.freeranger.range.lift.data.Record
import dev.freeranger.range.lift.data.WorkoutLog
import dev.freeranger.range.lift.data.WorkoutSet
import dev.freeranger.range.lift.data.newRecords
import dev.freeranger.range.lift.data.previousSets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WorkoutState(
    val log: WorkoutLog? = null,
    /** For each exercise in the workout (by entry id), what was done last time: the placeholders in its sets. */
    val previous: Map<Long, List<WorkoutSet>> = emptyMap(),
    /** The records this workout's ticked-off sets set, so far. */
    val records: List<Record> = emptyList(),
    /** Another workout is in progress, so this one can't be repeated yet. */
    val busy: Boolean = false,
)

class WorkoutViewModel(
    private val id: Long,
    private val repository: LiftRepository,
    /** Outlives this screen, for changes made right before leaving it. */
    private val appScope: CoroutineScope,
) : ViewModel() {
    val state: StateFlow<WorkoutState> = repository.logs.map { logs ->
        val log = logs.firstOrNull { it.workout.id == id }
        val previous = log?.exercises.orEmpty().associate { it.entry.id to previousSets(logs, it.exercise.id, log!!.workout.startedAt) }
        WorkoutState(log, previous, log?.let { newRecords(it, logs) }.orEmpty(), busy = logs.any { !it.workout.isFinished && it.workout.id != id })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WorkoutState())

    fun rename(name: String) {
        appScope.launch { repository.rename(id, name) }
    }

    fun addSet(entryId: Long) {
        viewModelScope.launch { repository.addSet(entryId) }
    }

    fun updateSet(set: WorkoutSet) {
        appScope.launch { repository.updateSet(set) }
    }

    fun deleteSet(set: WorkoutSet) {
        viewModelScope.launch { repository.deleteSet(set.id) }
    }

    fun restoreSet(set: WorkoutSet) {
        viewModelScope.launch { repository.restoreSet(set) }
    }

    fun reorder(entryIds: List<Long>) {
        appScope.launch { repository.reorder(entryIds) }
    }

    fun removeExercise(logged: LoggedExercise) {
        viewModelScope.launch { repository.removeFromWorkout(logged.entry.id) }
    }

    fun restoreExercise(logged: LoggedExercise) {
        viewModelScope.launch { repository.restoreExercise(logged) }
    }

    fun tidy() {
        appScope.launch { repository.tidy(id) }
    }

    fun finish() {
        appScope.launch { repository.finish(id) }
    }

    /** Restarts the rest stopwatch after a set. */
    fun setDone() {
        appScope.launch { repository.setDone(id) }
    }

    fun setNotes(notes: String) {
        appScope.launch { repository.setNotes(id, notes) }
    }

    fun saveTemplate(name: String) {
        appScope.launch { repository.saveTemplate(id, name) }
    }

    /** Starts this workout again and hands over the new one's id to open it. */
    fun repeat(onStarted: (Long) -> Unit) {
        viewModelScope.launch { repository.repeat(id)?.let(onStarted) }
    }

    fun delete() {
        appScope.launch { repository.delete(id) }
    }
}
