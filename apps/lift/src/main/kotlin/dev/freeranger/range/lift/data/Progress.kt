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
        .mapNotNull { log ->
            val sets = log.exercises.filter { it.exercise.id == exercise.id }.flatMap { it.sets }.filter { it.done }
            if (sets.isEmpty()) null else log.workout.startedAt to sets
        }
        .sortedBy { it.first }
    val metric = when {
        exercise.measure == Measure.Time -> if (exercise.load == Load.Weight) ProgressMetric.Weight else ProgressMetric.Time
        exercise.load == Load.Weight -> ProgressMetric.OneRepMax
        exercise.load == Load.Assisted -> ProgressMetric.Assistance
        // Once extra weight goes on, reps alone stop telling the story.
        exercise.load == Load.ExtraWeight && sessions.any { (_, sets) -> sets.any { (it.weight ?: 0.0) > 0 } } -> ProgressMetric.Weight
        else -> ProgressMetric.Reps
    }
    val points = sessions.mapNotNull { (at, sets) ->
        val scored = sets.mapNotNull { set -> metric.valueOf(set)?.let { set to it } }
        val best = if (metric == ProgressMetric.Assistance) scored.minByOrNull { it.second } else scored.maxByOrNull { it.second }
        best?.let { (set, value) -> ProgressPoint(at, value, set) }
    }
    return Progression(metric, points)
}

private fun ProgressMetric.valueOf(set: WorkoutSet): Double? = when (this) {
    ProgressMetric.OneRepMax -> set.estimatedMax
    ProgressMetric.Weight -> set.weight?.takeIf { it > 0 }
    ProgressMetric.Reps -> set.reps?.takeIf { it > 0 }?.toDouble()
    ProgressMetric.Time -> set.seconds?.takeIf { it > 0 }?.toDouble()
    // No help logged means none was needed.
    ProgressMetric.Assistance -> if ((set.reps ?: 0) > 0) set.weight ?: 0.0 else null
}
