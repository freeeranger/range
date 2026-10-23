package dev.freeranger.range.lift.ui.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.freeranger.range.lift.data.Exercise
import dev.freeranger.range.lift.data.LiftRepository
import dev.freeranger.range.lift.data.Progression
import dev.freeranger.range.lift.data.Workout
import dev.freeranger.range.lift.data.WorkoutSet
import dev.freeranger.range.lift.data.bestSets
import dev.freeranger.range.lift.data.progression
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PickerState(val exercises: List<Exercise> = emptyList(), val bests: Map<Long, WorkoutSet> = emptyMap())

class ExercisePickerViewModel(
    private val workoutId: Long,
    private val repository: LiftRepository,
    /** Outlives this screen, which closes as soon as an exercise is picked. */
    private val appScope: CoroutineScope,
) : ViewModel() {
    val state: StateFlow<PickerState> = combine(repository.exercises, repository.logs) { exercises, logs ->
        PickerState(exercises, bestSets(logs))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PickerState())

    fun pick(exerciseId: Long) {
        appScope.launch { repository.addToWorkout(workoutId, exerciseId) }
    }
}

class ExerciseEditViewModel(
    private val id: Long?,
    private val workoutId: Long?,
    private val repository: LiftRepository,
    /** Outlives this screen, which closes as soon as Save is tapped. */
    private val appScope: CoroutineScope,
) : ViewModel() {
    val exercise: StateFlow<Exercise?> = repository.exercises
        .map { all -> all.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Saves the changes, or adds the new exercise to the library and, if one is open, the workout. */
    fun save(exercise: Exercise) {
        appScope.launch {
            if (id != null) {
                repository.updateExercise(exercise.copy(id = id))
            } else {
                val added = repository.addExercise(exercise)
                if (workoutId != null) repository.addToWorkout(workoutId, added)
            }
        }
    }
}

/** One workout's finished sets of the exercise. */
data class Session(val workout: Workout, val sets: List<WorkoutSet>)

data class ExerciseDetailState(
    val exercise: Exercise? = null,
    /** Newest first. */
    val sessions: List<Session> = emptyList(),
    val best: WorkoutSet? = null,
    val progression: Progression? = null,
)

class ExerciseDetailViewModel(
    private val id: Long,
    private val repository: LiftRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {
    val state: StateFlow<ExerciseDetailState> = combine(repository.exercises, repository.logs) { exercises, logs ->
        val sessions = logs.filter { it.workout.isFinished }.mapNotNull { log ->
            val sets = log.exercises.filter { it.exercise.id == id }.flatMap { it.sets }.filter { it.done }
            if (sets.isEmpty()) null else Session(log.workout, sets)
        }
        val exercise = exercises.firstOrNull { it.id == id }
        ExerciseDetailState(exercise, sessions, bestSets(logs)[id], exercise?.let { progression(logs, it) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailState())

    fun setRemoved(removed: Boolean) {
        appScope.launch { repository.setRemoved(id, removed) }
    }
}
