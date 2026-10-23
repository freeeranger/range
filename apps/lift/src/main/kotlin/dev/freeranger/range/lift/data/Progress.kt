package dev.freeranger.range.lift.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** A week of training, from [start], its first day. Finished workouts only. */
data class WeekTotals(val start: LocalDate, val workouts: Int, val sets: Int, val volume: Double)

/** The training in the week that has [today]. */
fun weekTotals(logs: List<WorkoutLog>, today: LocalDate, firstDayOfWeek: DayOfWeek, zone: ZoneId): WeekTotals {
    val start = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val week = logs.filter { log ->
        log.workout.isFinished && log.workout.startedAt.atZone(zone).toLocalDate().let { it >= start && it < start.plusWeeks(1) }
    }
    return WeekTotals(start, week.size, week.sumOf { it.doneSets.size }, week.sumOf { it.volume })
}

/**
 * The one number per workout that shows getting stronger at an exercise, chosen by how it's
 * logged, so sets of different weights and reps can be compared.
 */
enum class ProgressMetric {
    /** Weight lifted for reps: the most the best set says could be lifted once (Epley), so 80 × 5 and 90 × 2 compare. */
    OneRepMax,

    /** The heaviest weight: for timed sets with a weight, and for bodyweight exercises once extra weight is added. */
    Weight,

    /** The most reps in a set: bodyweight exercises without extra weight. */
    Reps,

    /** The longest set: timed exercises. */
    Time,

    /** The least help in a set: assisted exercises. Going down is progress. */
    Assistance,
}

/** A workout's best set by the [ProgressMetric], and its [value]. */
data class ProgressPoint(val at: Instant, val value: Double, val set: WorkoutSet)

/** An exercise's progress: one point per finished workout it was done in, oldest first. */
data class Progression(val metric: ProgressMetric, val points: List<ProgressPoint>)

fun progression(logs: List<WorkoutLog>, exercise: Exercise): Progression {
    val sessions = logs.filter { it.workout.isFinished }
        .mapNotNull { log -> log.doneSetsOf(exercise.id).takeIf { it.isNotEmpty() }?.let { log.workout.startedAt to it } }
        .sortedBy { it.first }
    val metric = exercise.progressMetric(sessions.flatMap { it.second })
    val points = sessions.mapNotNull { (at, sets) -> metric.best(sets)?.let { (set, value) -> ProgressPoint(at, value, set) } }
    return Progression(metric, points)
}

/** The finished sets of an exercise in this workout. */
fun WorkoutLog.doneSetsOf(exerciseId: Long): List<WorkoutSet> =
    exercises.filter { it.exercise.id == exerciseId }.flatMap { it.sets }.filter { it.done }

/** The number that shows progress at this exercise, given the [sets] done of it so far. */
fun Exercise.progressMetric(sets: List<WorkoutSet>): ProgressMetric = when {
    measure == Measure.Time -> if (load == Load.Weight) ProgressMetric.Weight else ProgressMetric.Time
    load == Load.Weight -> ProgressMetric.OneRepMax
    load == Load.Assisted -> ProgressMetric.Assistance
    // Once extra weight goes on, reps alone stop telling the story.
    load == Load.ExtraWeight && sets.any { (it.weight ?: 0.0) > 0 } -> ProgressMetric.Weight
    else -> ProgressMetric.Reps
}

/** Every number this exercise keeps a record of. */
fun Exercise.recordMetrics(): List<ProgressMetric> = when (load) {
    Load.Weight -> if (measure == Measure.Reps) listOf(ProgressMetric.OneRepMax, ProgressMetric.Weight) else listOf(ProgressMetric.Weight, ProgressMetric.Time)
    Load.Bodyweight -> listOf(if (measure == Measure.Reps) ProgressMetric.Reps else ProgressMetric.Time)
    Load.ExtraWeight -> listOf(ProgressMetric.Weight, if (measure == Measure.Reps) ProgressMetric.Reps else ProgressMetric.Time)
    Load.Assisted -> listOf(ProgressMetric.Assistance)
}

/** Less help is better; for everything else, more is. */
fun ProgressMetric.isBetter(value: Double, than: Double): Boolean = if (this == ProgressMetric.Assistance) value < than else value > than

/** The best of [sets] by this measure, and its value. */
fun ProgressMetric.best(sets: List<WorkoutSet>): Pair<WorkoutSet, Double>? =
    sets.mapNotNull { set -> valueOf(set)?.let { set to it } }
        .reduceOrNull { best, next -> if (isBetter(next.second, best.second)) next else best }

fun ProgressMetric.valueOf(set: WorkoutSet): Double? = when (this) {
    ProgressMetric.OneRepMax -> set.estimatedMax
    ProgressMetric.Weight -> set.weight?.takeIf { it > 0 }
    ProgressMetric.Reps -> set.reps?.takeIf { it > 0 }?.toDouble()
    ProgressMetric.Time -> set.seconds?.takeIf { it > 0 }?.toDouble()
    // No help logged means none was needed.
    ProgressMetric.Assistance -> if ((set.reps ?: 0) > 0 || (set.seconds ?: 0) > 0) set.weight ?: 0.0 else null
}

/** A best ever: the set, its value by [metric], what the record was before it (null if there was none), and when. */
data class Record(val exercise: Exercise, val metric: ProgressMetric, val set: WorkoutSet, val value: Double, val previous: Double?, val at: Instant)

/**
 * The records [log] set: for each exercise, its best set by each measure the exercise keeps,
 * where that beats every finished workout before it. Doing an exercise for the first time sets
 * no records; there's nothing to beat.
 */
fun newRecords(log: WorkoutLog, logs: List<WorkoutLog>): List<Record> {
    val earlier = logs.filter { it.workout.isFinished && it.workout.id != log.workout.id && it.workout.startedAt < log.workout.startedAt }
    return log.exercises.map { it.exercise }.distinctBy { it.id }.flatMap { exercise ->
        val before = earlier.flatMap { it.doneSetsOf(exercise.id) }
        if (before.isEmpty()) return@flatMap emptyList()
        val today = log.doneSetsOf(exercise.id)
        exercise.recordMetrics().mapNotNull { metric ->
            val (set, value) = metric.best(today) ?: return@mapNotNull null
            val previous = metric.best(before)?.second
            if (previous == null || metric.isBetter(value, previous)) Record(exercise, metric, set, value, previous, log.workout.startedAt) else null
        }
    }
}

/** An exercise's standing records, each with the workout it was set in. */
fun records(logs: List<WorkoutLog>, exercise: Exercise): List<Record> {
    val sets = logs.filter { it.workout.isFinished }.flatMap { log -> log.doneSetsOf(exercise.id).map { it to log.workout.startedAt } }
    val at = sets.associate { (set, at) -> set.id to at }
    return exercise.recordMetrics().mapNotNull { metric ->
        val (set, value) = metric.best(sets.map { it.first }) ?: return@mapNotNull null
        Record(exercise, metric, set, value, previous = null, at = at.getValue(set.id))
    }
}

/** How an exercise went today against the last workout it was done in. [last] is null the first time. */
data class Comparison(val exercise: Exercise, val metric: ProgressMetric, val set: WorkoutSet, val today: Double, val last: Double?)

/** Each exercise in [log], in order, against the last finished workout before it that had it. */
fun comparisons(log: WorkoutLog, logs: List<WorkoutLog>): List<Comparison> {
    val earlier = logs.filter { it.workout.isFinished && it.workout.id != log.workout.id && it.workout.startedAt < log.workout.startedAt }
        .sortedByDescending { it.workout.startedAt }
    return log.exercises.map { it.exercise }.distinctBy { it.id }.mapNotNull { exercise ->
        val today = log.doneSetsOf(exercise.id)
        val previous = earlier.firstNotNullOfOrNull { it.doneSetsOf(exercise.id).takeIf { sets -> sets.isNotEmpty() } }.orEmpty()
        val metric = exercise.progressMetric(today + previous)
        val (set, value) = metric.best(today) ?: return@mapNotNull null
        Comparison(exercise, metric, set, value, metric.best(previous)?.second)
    }
}

/** The exercises done most recently, newest first, for the top of the exercise picker. Removed ones are left out. */
fun recentExercises(logs: List<WorkoutLog>, limit: Int = 6): List<Exercise> =
    logs.filter { it.workout.isFinished }
        .sortedByDescending { it.workout.startedAt }
        .flatMap { log -> log.exercises.map { it.exercise } }
        .filterNot { it.archived }
        .distinctBy { it.id }
        .take(limit)
