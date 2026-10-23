package dev.freeranger.range.lift.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock

/** The only way the app changes workouts, exercises and templates. */
class LiftRepository(
    private val dao: LiftDao,
    private val stopwatch: RestStopwatch = RestStopwatch.None,
    private val clock: () -> Clock = { Clock.systemDefaultZone() },
) {
    val exercises: Flow<List<Exercise>> = dao.observeExercises()

    /** Every template with its exercises, oldest first. */
    val templates: Flow<List<WorkoutTemplate>> =
        combine(dao.observeTemplates(), dao.observeTemplateExercises(), dao.observeExercises(), ::workoutTemplates)

    /** Every workout with what was logged in it, newest first. */
    val logs: Flow<List<WorkoutLog>> =
        combine(dao.observeWorkouts(), dao.observeEntries(), dao.observeSets(), dao.observeExercises(), ::workoutLogs)

    suspend fun workout(id: Long): Workout? = dao.workout(id)

    fun log(id: Long): Flow<WorkoutLog?> = logs.map { logs -> logs.firstOrNull { it.workout.id == id } }

    /** Adds any built-in exercise the library doesn't have yet. Run at every launch; cheap when there's nothing to add. */
    suspend fun addBuiltInExercises() {
        dao.insertBuiltIn(BuiltInExercises.all.map { it.toExercise() })
    }

    suspend fun addExercise(exercise: Exercise): Long = dao.insert(exercise.copy(id = 0, key = null, name = exercise.name.trim()))

    /** Saves changes to an exercise's name and type. Its key and whether it's removed stay as they are. */
    suspend fun updateExercise(exercise: Exercise) {
        val stored = dao.exercise(exercise.id) ?: return
        dao.update(exercise.copy(key = stored.key, name = exercise.name.trim(), archived = stored.archived))
    }

    /** Takes an exercise out of the library, or puts it back. Past workouts keep it either way. */
    suspend fun setRemoved(exerciseId: Long, removed: Boolean) {
        val stored = dao.exercise(exerciseId) ?: return
        dao.update(stored.copy(archived = removed))
    }

    /** Starts a workout, or returns the one already in progress: only one runs at a time. */
    suspend fun start(): Long = dao.inProgress()?.id ?: dao.insert(Workout(startedAt = clock().instant()))

    suspend fun rename(workoutId: Long, name: String) {
        val workout = dao.workout(workoutId) ?: return
        dao.update(workout.copy(name = name.trim()))
    }

    /**
     * Adds an exercise to a workout with as many empty sets as were done last time, or one the
     * first time. Last time's numbers show in them as placeholders, not values.
     */
    suspend fun addToWorkout(workoutId: Long, exerciseId: Long) {
        val position = (dao.lastPosition(workoutId) ?: -1) + 1
        val count = dao.lastSets(exerciseId).size.coerceAtLeast(1)
        val sets = List(count) { WorkoutSet(workoutExerciseId = 0, position = it) }
        dao.insert(WorkoutExercise(workoutId = workoutId, exerciseId = exerciseId, position = position), sets)
    }

    suspend fun removeFromWorkout(entryId: Long) = dao.deleteEntry(entryId)

    /** Puts a workout's exercises in the order of [entryIds]. */
    suspend fun reorder(entryIds: List<Long>) = dao.reorder(entryIds)

    /** Adds an empty set at the end. */
    suspend fun addSet(entryId: Long) {
        val last = dao.sets(entryId).lastOrNull()
        dao.insert(WorkoutSet(workoutExerciseId = entryId, position = (last?.position ?: -1) + 1))
    }

    suspend fun updateSet(set: WorkoutSet) = dao.update(set)

    suspend fun deleteSet(id: Long) = dao.deleteSet(id)

    /** Ends a workout and its rest stopwatch. Every set should be ticked off by now; any that aren't are dropped. */
    suspend fun finish(workoutId: Long) {
        val workout = dao.workout(workoutId) ?: return
        if (workout.isFinished) return
        dao.finish(workout.copy(finishedAt = clock().instant(), lastSetAt = null))
        stopwatch.cancel()
    }

    suspend fun delete(workoutId: Long) {
        if (dao.workout(workoutId)?.isFinished == false) stopwatch.cancel()
        dao.deleteWorkout(workoutId)
    }

    suspend fun setNotes(workoutId: Long, notes: String) {
        val workout = dao.workout(workoutId) ?: return
        if (workout.notes != notes.trim()) dao.update(workout.copy(notes = notes.trim()))
    }

    /** Restarts the rest stopwatch: a set of [label] was just ticked off. */
    suspend fun setDone(workoutId: Long, label: String) {
        val workout = dao.workout(workoutId)?.takeIf { !it.isFinished } ?: return
        val updated = workout.copy(lastSetAt = clock().instant())
        dao.update(updated)
        stopwatch.show(updated, label)
    }

    /** Saves [workoutId]'s exercises and set counts as a new template, and links the workout to it. */
    suspend fun saveTemplate(workoutId: Long, name: String): Long? {
        val log = log(workoutId).first() ?: return null
        val plan = log.plan().ifEmpty { return null }
        val id = dao.insert(Template(name = name.trim(), createdAt = clock().instant()), plan.toTemplateExercises())
        dao.update(log.workout.copy(templateId = id))
        return id
    }

    /** Makes a template's exercises and set counts those of [workoutId]. */
    suspend fun updateTemplate(templateId: Long, workoutId: Long) {
        val plan = log(workoutId).first()?.plan()?.takeIf { it.isNotEmpty() } ?: return
        dao.replace(templateId, plan.toTemplateExercises())
    }

    suspend fun renameTemplate(templateId: Long, name: String) {
        val template = dao.template(templateId) ?: return
        dao.update(template.copy(name = name.trim()))
    }

    suspend fun deleteTemplate(templateId: Long) = dao.deleteTemplate(templateId)

    /**
     * Starts a workout from a template: named after it, with its exercises and as many empty sets
     * as it keeps, last time's numbers showing as placeholders. Returns the one in progress instead,
     * if there is one.
     */
    suspend fun startTemplate(templateId: Long): Long? {
        dao.inProgress()?.let { return it.id }
        val template = dao.template(templateId) ?: return null
        val entries = dao.templateExercises(templateId).mapIndexed { position, entry ->
            WorkoutExercise(workoutId = 0, exerciseId = entry.exerciseId, position = position) to
                List(entry.sets.coerceAtLeast(1)) { WorkoutSet(workoutExerciseId = 0, position = it) }
        }
        return dao.insert(Workout(name = template.name, startedAt = clock().instant(), templateId = templateId), entries)
    }

    private fun List<Pair<Long, Int>>.toTemplateExercises() =
        mapIndexed { position, (exerciseId, sets) -> TemplateExercise(templateId = 0, exerciseId = exerciseId, position = position, sets = sets) }
}

/** Shows the time since the last set outside the app, counting up on its own, even on the lock screen. */
interface RestStopwatch {
    /** Starts counting from [workout]'s last set, of the exercise [label] names. */
    fun show(workout: Workout, label: String)

    fun cancel()

    /** For tests, and anywhere without a phone to show it on. */
    object None : RestStopwatch {
        override fun show(workout: Workout, label: String) {}
        override fun cancel() {}
    }
}
