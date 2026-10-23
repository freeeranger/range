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
        dao.insertBuiltIn(BuiltInExercises.all.map { (key, muscle) -> Exercise(key = key, muscle = muscle) })
    }

    suspend fun addExercise(name: String, muscle: Muscle): Long = dao.insert(Exercise(name = name.trim(), muscle = muscle))

    /** Starts a workout, or returns the one already in progress: only one runs at a time. */
    suspend fun start(): Long = dao.inProgress()?.id ?: dao.insert(Workout(startedAt = clock().instant()))

    suspend fun rename(workoutId: Long, name: String) {
        val workout = dao.workout(workoutId) ?: return
        dao.update(workout.copy(name = name.trim()))
    }

    /**
     * Adds an exercise to a workout with last time's sets filled in, ready to tick off or adjust,
     * or one empty set the first time.
     */
    suspend fun addToWorkout(workoutId: Long, exerciseId: Long) {
        val position = (dao.lastPosition(workoutId) ?: -1) + 1
        val sets = dao.lastSets(exerciseId)
            .mapIndexed { i, set -> WorkoutSet(workoutExerciseId = 0, position = i, weight = set.weight, reps = set.reps) }
            .ifEmpty { listOf(WorkoutSet(workoutExerciseId = 0, position = 0)) }
        dao.insert(WorkoutExercise(workoutId = workoutId, exerciseId = exerciseId, position = position), sets)
    }

    suspend fun removeFromWorkout(entryId: Long) = dao.deleteEntry(entryId)

    /** Adds a set copying the one above it, since the next set is usually the same again. */
    suspend fun addSet(entryId: Long) {
        val last = dao.sets(entryId).lastOrNull()
        dao.insert(WorkoutSet(workoutExerciseId = entryId, position = (last?.position ?: -1) + 1, weight = last?.weight, reps = last?.reps))
    }

    suspend fun updateSet(set: WorkoutSet) = dao.update(set)

    suspend fun deleteSet(id: Long) = dao.deleteSet(id)

    /** Ends a workout, keeping only the sets that were ticked off. */
    suspend fun finish(workoutId: Long) {
        val workout = dao.workout(workoutId) ?: return
        if (!workout.isFinished) dao.finish(workout.copy(finishedAt = clock().instant()))
    }

    suspend fun delete(workoutId: Long) = dao.deleteWorkout(workoutId)
}
