package dev.freeranger.range.lift.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.freeranger.range.lift.data.Exercise
import dev.freeranger.range.lift.data.LiftRepository
import dev.freeranger.range.lift.data.Muscle
import dev.freeranger.range.lift.data.WorkoutLog
import dev.freeranger.range.lift.data.WorkoutSet
import dev.freeranger.range.lift.data.bestSets
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeState(
    val logs: List<WorkoutLog> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    /** False until the database has answered, so the empty state doesn't flash on launch. */
    val loaded: Boolean = false,
) {
    val inProgress: WorkoutLog? get() = logs.firstOrNull { !it.workout.isFinished }
    val history: List<WorkoutLog> get() = logs.filter { it.workout.isFinished }
    val bests: Map<Long, WorkoutSet> by lazy { bestSets(logs) }
}

class HomeViewModel(private val repository: LiftRepository) : ViewModel() {
    val state: StateFlow<HomeState> = combine(repository.logs, repository.exercises) { logs, exercises ->
        HomeState(logs, exercises, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    /** Starts a workout, or picks up the one in progress, and hands over its id to open it. */
    fun start(onStarted: (Long) -> Unit) {
        viewModelScope.launch { onStarted(repository.start()) }
    }

    fun addExercise(name: String, muscle: Muscle) {
        viewModelScope.launch { repository.addExercise(name, muscle) }
    }
}
