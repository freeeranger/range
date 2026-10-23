package dev.freeranger.range.lift.data

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import java.time.Instant

/** A training session. In progress until [finishedAt] is set; at most one is in progress at a time. */
@Entity(tableName = "workouts")
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Empty until the user names it: shown as "Workout" in the app's language. */
    val name: String = "",
    val startedAt: Instant,
    val finishedAt: Instant? = null,
) {
    val isFinished: Boolean get() = finishedAt != null
}

/** An exercise done in a workout, in the order [position] puts it. The same exercise can appear twice. */
@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(entity = Workout::class, parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"]),
    ],
    indices = [Index("workoutId"), Index("exerciseId")],
)
data class WorkoutExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val position: Int,
)

/** One set: a weight lifted for a number of reps. Only [done] sets count once the workout is finished. */
@Entity(
    tableName = "sets",
    foreignKeys = [
        ForeignKey(entity = WorkoutExercise::class, parentColumns = ["id"], childColumns = ["workoutExerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("workoutExerciseId")],
)
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseId: Long,
    val position: Int,
    /** In kilograms. Null for bodyweight sets and sets not filled in yet. */
    val weight: Double? = null,
    val reps: Int? = null,
    val done: Boolean = false,
) {
    /**
     * The most this could be lifted once, by Epley's formula: what the best-set and 1RM figures
     * compare sets by. Null without both a weight and reps.
     */
    val estimatedMax: Double?
        get() {
            val kg = weight?.takeIf { it > 0 } ?: return null
            val reps = reps?.takeIf { it > 0 } ?: return null
            return if (reps == 1) kg else kg * (1 + reps / 30.0)
        }
}

/** An exercise in a workout with its sets, in order. */
data class LoggedExercise(val entry: WorkoutExercise, val exercise: Exercise, val sets: List<WorkoutSet>)

/** A workout with everything logged in it. */
data class WorkoutLog(val workout: Workout, val exercises: List<LoggedExercise>) {
    val doneSets: List<WorkoutSet> get() = exercises.flatMap { it.sets }.filter { it.done }

    /** Kilograms moved: weight times reps over every finished set. */
    val volume: Double get() = doneSets.sumOf { (it.weight ?: 0.0) * (it.reps ?: 0) }
}

/** Puts the four tables back together, newest workout first. */
fun workoutLogs(
    workouts: List<Workout>,
    entries: List<WorkoutExercise>,
    sets: List<WorkoutSet>,
    exercises: List<Exercise>,
): List<WorkoutLog> {
    val exercisesById = exercises.associateBy { it.id }
    val setsByEntry = sets.groupBy { it.workoutExerciseId }
    val entriesByWorkout = entries.groupBy { it.workoutId }
    return workouts.sortedByDescending { it.startedAt }.map { workout ->
        val logged = entriesByWorkout[workout.id].orEmpty().sortedBy { it.position }.mapNotNull { entry ->
            val exercise = exercisesById[entry.exerciseId] ?: return@mapNotNull null
            LoggedExercise(entry, exercise, setsByEntry[entry.id].orEmpty().sortedBy { it.position })
        }
        WorkoutLog(workout, logged)
    }
}

/** Each exercise's heaviest finished set by estimated one-rep max, from finished workouts only. */
fun bestSets(logs: List<WorkoutLog>): Map<Long, WorkoutSet> =
    logs.filter { it.workout.isFinished }
        .flatMap { log -> log.exercises.flatMap { logged -> logged.sets.filter { it.done }.map { logged.exercise.id to it } } }
        .filter { it.second.estimatedMax != null }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, sets) -> sets.maxBy { it.estimatedMax!! } }

/** What was done on [exerciseId] the last finished time before [before]: the "Previous" column while logging. */
fun previousSets(logs: List<WorkoutLog>, exerciseId: Long, before: Instant): List<WorkoutSet> =
    logs.asSequence()
        .filter { it.workout.isFinished && it.workout.startedAt < before }
        .map { log -> log.exercises.filter { it.exercise.id == exerciseId }.flatMap { it.sets }.filter { it.done } }
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()
