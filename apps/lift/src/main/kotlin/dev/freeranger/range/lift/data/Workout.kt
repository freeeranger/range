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

/**
 * One set: reps or seconds, depending on the exercise's [Measure], with a weight if it takes one.
 * Only [done] sets count once the workout is finished.
 */
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
    /** In kilograms: the weight lifted, the extra weight, or the help, by the exercise's [Load]. Null if none. */
    val weight: Double? = null,
    /** For exercises measured in reps. */
    val reps: Int? = null,
    val done: Boolean = false,
    /** For exercises measured in time. */
    val seconds: Int? = null,
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
data class LoggedExercise(val entry: WorkoutExercise, val exercise: Exercise, val sets: List<WorkoutSet>) {
    /**
     * Kilograms moved: weight times reps over the finished sets, both sides counted. Only weights
     * actually lifted count, so bodyweight, assisted and timed sets add nothing.
     */
    val volume: Double
        get() {
            if (exercise.measure != Measure.Reps || exercise.load !in setOf(Load.Weight, Load.ExtraWeight)) return 0.0
            val sides = if (exercise.perSide) 2 else 1
            return sets.filter { it.done }.sumOf { (it.weight ?: 0.0) * (it.reps ?: 0) } * sides
        }

    /** The best finished set, by the exercise's own [Exercise.setOrder]. */
    val best: WorkoutSet? get() = sets.filter { it.done }.maxWithOrNull(exercise.setOrder)
}

/** A workout with everything logged in it. */
data class WorkoutLog(val workout: Workout, val exercises: List<LoggedExercise>) {
    val doneSets: List<WorkoutSet> get() = exercises.flatMap { it.sets }.filter { it.done }

    val volume: Double get() = exercises.sumOf { it.volume }
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

/** Each exercise's best finished set, from finished workouts only. */
fun bestSets(logs: List<WorkoutLog>): Map<Long, WorkoutSet> =
    logs.filter { it.workout.isFinished }
        .flatMap { it.exercises }
        .groupBy { it.exercise.id }
        .mapNotNull { (id, logged) -> logged.flatMap { it.sets }.filter { it.done }.maxWithOrNull(logged.first().exercise.setOrder)?.let { id to it } }
        .toMap()

/** What was done on [exerciseId] the last finished time before [before]: the placeholders while logging. */
fun previousSets(logs: List<WorkoutLog>, exerciseId: Long, before: Instant): List<WorkoutSet> =
    logs.asSequence()
        .filter { it.workout.isFinished && it.workout.startedAt < before }
        .map { log -> log.exercises.filter { it.exercise.id == exerciseId }.flatMap { it.sets }.filter { it.done } }
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()

/**
 * What to show in each set's empty fields: the same set last time, or else the set above (its
 * numbers, or its own placeholder), since the next set is usually the same again.
 */
fun placeholders(sets: List<WorkoutSet>, previous: List<WorkoutSet>): List<WorkoutSet?> {
    val shown = ArrayList<WorkoutSet?>(sets.size)
    sets.forEachIndexed { i, set ->
        val above = sets.getOrNull(i - 1)?.let { it.filledFrom(shown[i - 1]) }
        shown += previous.getOrNull(i) ?: above?.takeIf { it.reps != null || it.seconds != null || it.weight != null }
    }
    return shown
}

/** This set with any empty number taken from [placeholder]: what ticking it off saves. */
fun WorkoutSet.filledFrom(placeholder: WorkoutSet?): WorkoutSet = copy(
    weight = weight ?: placeholder?.weight,
    reps = reps ?: placeholder?.reps,
    seconds = seconds ?: placeholder?.seconds,
)

/** The set has what [exercise] counts: reps or a time. Weight is optional, so a forgotten one doesn't block ticking. */
fun WorkoutSet.isComplete(exercise: Exercise): Boolean = when (exercise.measure) {
    Measure.Reps -> (reps ?: 0) > 0
    Measure.Time -> (seconds ?: 0) > 0
}
