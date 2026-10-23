package dev.freeranger.range.lift.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.freeranger.range.lift.data.Exercise
import dev.freeranger.range.lift.data.LiftRepository
import dev.freeranger.range.lift.data.WorkoutLog
import dev.freeranger.range.lift.data.WorkoutTemplate
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
    val templates: List<WorkoutTemplate> = emptyList(),
    /** False until the database has answered, so the empty state doesn't flash on launch. */
    val loaded: Boolean = false,
) {
    val inProgress: WorkoutLog? get() = logs.firstOrNull { !it.workout.isFinished }
    val history: List<WorkoutLog> get() = logs.filter { it.workout.isFinished }
    val bests: Map<Long, WorkoutSet> by lazy { bestSets(logs) }
}

class HomeViewModel(private val repository: LiftRepository) : ViewModel() {
    val state: StateFlow<HomeState> = combine(repository.logs, repository.exercises, repository.templates) { logs, exercises, templates ->
        HomeState(logs, exercises, templates, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    /** Starts a workout, or picks up the one in progress, and hands over its id to open it. */
    fun start(onStarted: (Long) -> Unit) {
        viewModelScope.launch { onStarted(repository.start()) }
    }

    /** Starts a workout from a template and hands over its id to open it. */
    fun startTemplate(templateId: Long, onStarted: (Long) -> Unit) {
        viewModelScope.launch { repository.startTemplate(templateId)?.let(onStarted) }
    }

    fun renameTemplate(templateId: Long, name: String) {
        viewModelScope.launch { repository.renameTemplate(templateId, name) }
    }

    fun deleteTemplate(templateId: Long) {
        viewModelScope.launch { repository.deleteTemplate(templateId) }
    }
}
