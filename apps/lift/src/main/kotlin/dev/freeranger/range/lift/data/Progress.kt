package dev.freeranger.range.lift.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/*
 * The figures on the home page, from finished workouts only: a workout in progress counts once
 * it's finished.
 */

/** A week of training, from [start], its first day. */
data class WeekTotals(val start: LocalDate, val workouts: Int, val sets: Int, val volume: Double)

/** The last [weeks] weeks up to and including the one with [today], oldest first. */
fun weeklyTotals(logs: List<WorkoutLog>, today: LocalDate, firstDayOfWeek: DayOfWeek, zone: ZoneId, weeks: Int = 12): List<WeekTotals> {
    fun weekOf(day: LocalDate) = day.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val byWeek = logs.filter { it.workout.isFinished }.groupBy { weekOf(it.workout.startedAt.atZone(zone).toLocalDate()) }
    val thisWeek = weekOf(today)
    return (weeks - 1 downTo 0).map { back ->
        val start = thisWeek.minusWeeks(back.toLong())
        val week = byWeek[start].orEmpty()
        WeekTotals(start, week.size, week.sumOf { it.doneSets.size }, week.sumOf { it.volume })
    }
}

/** An exercise's estimated one-rep max in one workout: its best set's. */
data class MaxPoint(val at: Instant, val kg: Double)

/** The exercise's estimated one-rep max in each finished workout it was done in, oldest first. */
fun oneRepMaxHistory(logs: List<WorkoutLog>, exerciseId: Long): List<MaxPoint> =
    logs.filter { it.workout.isFinished }
        .mapNotNull { log ->
            log.exercises.filter { it.exercise.id == exerciseId }
                .flatMap { it.sets }
                .filter { it.done }
                .mapNotNull { it.estimatedMax }
                .maxOrNull()
                ?.let { MaxPoint(log.workout.startedAt, it) }
        }
        .sortedBy { it.at }

/**
 * The exercises worth charting a one-rep max for: weight lifted for reps, done most often first
 * (most recently, between equals), at most [limit].
 */
fun progressExercises(logs: List<WorkoutLog>, limit: Int = 4): List<Exercise> =
    logs.filter { it.workout.isFinished }
        .flatMap { log -> log.exercises.filter { e -> e.exercise.hasOneRepMax && e.sets.any { it.done && it.estimatedMax != null } }.map { log to it.exercise } }
        .groupBy({ it.second.id }, { it })
        .values
        .sortedWith(compareByDescending<List<Pair<WorkoutLog, Exercise>>> { it.size }.thenByDescending { group -> group.maxOf { it.first.workout.startedAt } })
        .take(limit)
        .map { it.first().second }

/** Finished sets per muscle group since [since], every group included, most first. */
fun setsPerMuscle(logs: List<WorkoutLog>, since: Instant): List<Pair<Muscle, Int>> {
    val counts = logs.filter { it.workout.isFinished && it.workout.startedAt >= since }
        .flatMap { it.exercises }
        .groupBy({ it.exercise.muscle }, { logged -> logged.sets.count { it.done } })
        .mapValues { it.value.sum() }
    return Muscle.entries.map { it to (counts[it] ?: 0) }.sortedByDescending { it.second }
}
