package dev.freeranger.range.lift.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.freeranger.range.lift.data.LiftRepository
import dev.freeranger.range.lift.data.WorkoutLog
import dev.freeranger.range.lift.data.WorkoutSet
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
        WorkoutState(log, previous)
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

    fun deleteSet(setId: Long) {
        viewModelScope.launch { repository.deleteSet(setId) }
    }

    fun reorder(entryIds: List<Long>) {
        appScope.launch { repository.reorder(entryIds) }
    }

    fun removeExercise(entryId: Long) {
        viewModelScope.launch { repository.removeFromWorkout(entryId) }
    }

    fun finish() {
        appScope.launch { repository.finish(id) }
    }

    fun delete() {
        appScope.launch { repository.delete(id) }
    }
}
