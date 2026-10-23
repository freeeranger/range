package dev.freeranger.range.lift.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock

/** The only way the app changes workouts and exercises. */
class LiftRepository(
    private val dao: LiftDao,
    private val clock: () -> Clock = { Clock.systemDefaultZone() },
) {
    val exercises: Flow<List<Exercise>> = dao.observeExercises()

    /** Every workout with what was logged in it, newest first. */
    val logs: Flow<List<WorkoutLog>> =
        combine(dao.observeWorkouts(), dao.observeEntries(), dao.observeSets(), dao.observeExercises(), ::workoutLogs)

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

    /** Ends a workout. Every set should be ticked off by now; any that aren't are dropped. */
    suspend fun finish(workoutId: Long) {
        val workout = dao.workout(workoutId) ?: return
        if (!workout.isFinished) dao.finish(workout.copy(finishedAt = clock().instant()))
    }

    suspend fun delete(workoutId: Long) = dao.deleteWorkout(workoutId)
}
