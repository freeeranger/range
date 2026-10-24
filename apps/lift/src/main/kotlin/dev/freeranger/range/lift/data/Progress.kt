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

/** Sets ticked off for each muscle in the week that has [today], finished workouts only. Every muscle is there, untrained ones at zero. */
fun weekSetsByMuscle(logs: List<WorkoutLog>, today: LocalDate, firstDayOfWeek: DayOfWeek, zone: ZoneId): Map<Muscle, Int> {
    val start = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val counts = logs.filter { log ->
        log.workout.isFinished && log.workout.startedAt.atZone(zone).toLocalDate().let { it >= start && it < start.plusWeeks(1) }
    }.flatMap { it.exercises }.groupBy { it.exercise.muscle }.mapValues { (_, logged) -> logged.sumOf { e -> e.sets.count { it.done } } }
    return Muscle.entries.associateWith { counts[it] ?: 0 }
}

/** Finished workouts in the week starting [start]. */
data class WeekCount(val start: LocalDate, val workouts: Int)

/** How many workouts were finished each week, for the [weeks] weeks up to the one with [today], oldest first. */
fun weeklyWorkouts(logs: List<WorkoutLog>, today: LocalDate, firstDayOfWeek: DayOfWeek, zone: ZoneId, weeks: Int = 12): List<WeekCount> {
    val thisWeek = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val counts = logs.filter { it.workout.isFinished }
        .groupingBy { it.workout.startedAt.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(firstDayOfWeek)) }
        .eachCount()
    return (weeks - 1 downTo 0).map { back -> thisWeek.minusWeeks(back.toLong()).let { WeekCount(it, counts[it] ?: 0) } }
}

/**
 * The usual number of workouts a week: the average over the finished weeks in [weeks] since the
 * first one trained. Null until a whole week has passed since then.
 */
fun averagePerWeek(weeks: List<WeekCount>): Double? {
    val since = weeks.dropLast(1).dropWhile { it.workouts == 0 }
    return if (since.isEmpty()) null else since.sumOf { it.workouts }.toDouble() / since.size
}

/**
 * The one number per workout that shows getting stronger at an exercise, chosen by how it's
 * logged, so sets of different weights and reps can be compared.
 */
enum class ProgressMetric {
    /** Weight lifted for reps: the most the best set says could be lifted once (Epley), so 80 × 5 and 90 × 2 compare. */
    OneRepMax,

    /** The heaviest weight: weights held for time. */
    Weight,

    /** The most added to bodyweight, or the least help, once either is logged: −20 kg up to +10 kg is progress. */
    AddedWeight,

    /** The most reps in a set: bodyweight exercises with nothing added or taken off. */
    Reps,

    /** The longest set: timed exercises. */
    Time,
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
    load == Load.Weight -> if (measure == Measure.Time) ProgressMetric.Weight else ProgressMetric.OneRepMax
    measure == Measure.Time -> ProgressMetric.Time
    // Once weight goes on or help comes off, reps alone stop telling the story.
    sets.addsWeight() -> ProgressMetric.AddedWeight
    else -> ProgressMetric.Reps
}

/** Every number this exercise keeps a record of, given the [sets] done of it. */
fun Exercise.recordMetrics(sets: List<WorkoutSet>): List<ProgressMetric> {
    val amount = if (measure == Measure.Reps) ProgressMetric.Reps else ProgressMetric.Time
    return when (load) {
        Load.Weight -> if (measure == Measure.Reps) listOf(ProgressMetric.OneRepMax, ProgressMetric.Weight) else listOf(ProgressMetric.Weight, ProgressMetric.Time)
        Load.Bodyweight -> listOfNotNull(ProgressMetric.AddedWeight.takeIf { sets.addsWeight() }, amount)
    }
}

/** Some of these bodyweight sets had weight added or help taken. */
private fun List<WorkoutSet>.addsWeight(): Boolean = any { (it.weight ?: 0.0) != 0.0 }

/** More is better, for every measure: for added weight, more on and less help. */
fun ProgressMetric.isBetter(value: Double, than: Double): Boolean = value > than

/** The best of [sets] by this measure, and its value. */
fun ProgressMetric.best(sets: List<WorkoutSet>): Pair<WorkoutSet, Double>? =
    sets.mapNotNull { set -> valueOf(set)?.let { set to it } }
        .reduceOrNull { best, next -> if (isBetter(next.second, best.second)) next else best }

fun ProgressMetric.valueOf(set: WorkoutSet): Double? = when (this) {
    ProgressMetric.OneRepMax -> set.estimatedMax
    ProgressMetric.Weight -> set.weight?.takeIf { it > 0 }
    // Nothing logged means plain bodyweight.
    ProgressMetric.AddedWeight -> if ((set.reps ?: 0) > 0 || (set.seconds ?: 0) > 0) set.weight ?: 0.0 else null
    ProgressMetric.Reps -> set.reps?.takeIf { it > 0 }?.toDouble()
    ProgressMetric.Time -> set.seconds?.takeIf { it > 0 }?.toDouble()
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
        exercise.recordMetrics(before + today).mapNotNull { metric ->
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
    return exercise.recordMetrics(sets.map { it.first }).mapNotNull { metric ->
        val (set, value) = metric.best(sets.map { it.first }) ?: return@mapNotNull null
        Record(exercise, metric, set, value, previous = null, at = at.getValue(set.id))
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
