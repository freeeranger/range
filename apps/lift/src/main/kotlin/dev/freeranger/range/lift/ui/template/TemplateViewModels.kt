package dev.freeranger.range.lift.ui.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.freeranger.range.lift.data.LiftRepository
import dev.freeranger.range.lift.data.TemplateExercise
import dev.freeranger.range.lift.data.WorkoutLog
import dev.freeranger.range.lift.data.WorkoutSet
import dev.freeranger.range.lift.data.WorkoutTemplate
import dev.freeranger.range.lift.data.previousSets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

data class TemplatesState(
    val templates: List<WorkoutTemplate> = emptyList(),
    /** When each template was last done, by template id, from the workouts started from it. */
    val lastDone: Map<Long, Instant> = emptyMap(),
)

/** When each template was last done, from the finished workouts started from it. */
fun lastDone(logs: List<WorkoutLog>): Map<Long, Instant> =
    logs.filter { it.workout.isFinished && it.workout.templateId != null }
        .groupBy { it.workout.templateId!! }
        .mapValues { (_, logs) -> logs.maxOf { it.workout.startedAt } }

class TemplatesViewModel(private val repository: LiftRepository) : ViewModel() {
    val state: StateFlow<TemplatesState> = combine(repository.templates, repository.logs) { templates, logs ->
        TemplatesState(templates, lastDone(logs))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TemplatesState())

    /** Makes an empty template and hands over its id to open it. */
    fun create(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(repository.createTemplate(name)) }
    }
}

data class TemplateState(
    val template: WorkoutTemplate? = null,
    /** A workout is in progress, so this one can't start yet. */
    val busy: Boolean = false,
    val lastDone: Instant? = null,
    /** Each of its exercises' best set the last time it was done, by exercise id. */
    val lastBest: Map<Long, WorkoutSet> = emptyMap(),
)

class TemplateViewModel(
    private val id: Long,
    private val repository: LiftRepository,
    /** Outlives this screen, for changes made right before leaving it. */
    private val appScope: CoroutineScope,
) : ViewModel() {
    val state: StateFlow<TemplateState> = combine(repository.templates, repository.logs) { templates, logs ->
        val template = templates.firstOrNull { it.template.id == id }
        val lastBest = template?.exercises.orEmpty().map { it.exercise }.distinctBy { it.id }.mapNotNull { exercise ->
            previousSets(logs, exercise.id, Instant.MAX).maxWithOrNull(exercise.setOrder)?.let { exercise.id to it }
        }.toMap()
        TemplateState(template, logs.any { !it.workout.isFinished }, lastDone(logs)[id], lastBest)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TemplateState())

    fun rename(name: String) {
        appScope.launch { repository.renameTemplate(id, name) }
    }

    fun delete() {
        appScope.launch { repository.deleteTemplate(id) }
    }

    fun setSets(entryId: Long, sets: Int) {
        appScope.launch { repository.setTemplateSets(entryId, sets) }
    }

    fun remove(entry: TemplateExercise) {
        viewModelScope.launch { repository.removeFromTemplate(entry.id) }
    }

    fun restore(entry: TemplateExercise) {
        viewModelScope.launch { repository.restoreTemplateExercise(entry) }
    }

    fun reorder(entryIds: List<Long>) {
        appScope.launch { repository.reorderTemplate(entryIds) }
    }

    /** Starts a workout from the template and hands over its id to open it. */
    fun start(onStarted: (Long) -> Unit) {
        viewModelScope.launch { repository.startTemplate(id)?.let(onStarted) }
    }
}
