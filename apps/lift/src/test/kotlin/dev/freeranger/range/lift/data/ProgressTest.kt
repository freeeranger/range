package dev.freeranger.range.lift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

class ProgressTest {
    private val friday = LocalDate.of(2026, 10, 23)
    private val zone = ZoneOffset.UTC
    private val bench = Exercise(id = 1, muscle = Muscle.Chest)
    private val plank = Exercise(id = 2, muscle = Muscle.Core, measure = Measure.Time, load = Load.Bodyweight)
    private val pullUp = Exercise(id = 3, muscle = Muscle.Back, load = Load.ExtraWeight)
    private val assisted = Exercise(id = 4, muscle = Muscle.Back, load = Load.Assisted)
    private var nextId = 1L

    private fun workout(day: LocalDate, exercise: Exercise, sets: List<WorkoutSet>, finished: Boolean = true): WorkoutLog {
        val workout = Workout(nextId++, "", day.atTime(17, 0).toInstant(zone), if (finished) day.atTime(18, 0).toInstant(zone) else null)
        return WorkoutLog(workout, listOf(LoggedExercise(WorkoutExercise(nextId++, workout.id, exercise.id, 0), exercise, sets)))
    }

    private fun sets(vararg sets: Pair<Double?, Int>, done: Boolean = true) =
        sets.mapIndexed { i, (kg, reps) -> WorkoutSet(nextId++, 0, i, kg, reps, done) }

    @Test
    fun `this week's totals count finished workouts from the first day of the week`() {
        val logs = listOf(
            workout(friday, bench, sets(100.0 to 5, 100.0 to 5)),
            workout(friday.minusDays(4), bench, sets(80.0 to 10) + sets(80.0 to 10, done = false)),
            workout(friday.minusDays(5), bench, sets(80.0 to 10)),
            workout(friday, bench, sets(200.0 to 5), finished = false),
        )
        assertEquals(WeekTotals(LocalDate.of(2026, 10, 19), workouts = 2, sets = 3, volume = 1800.0), weekTotals(logs, friday, DayOfWeek.MONDAY, zone))
        assertEquals(3, weekTotals(logs, friday, DayOfWeek.SUNDAY, zone).workouts)
    }

    @Test
    fun `weight for reps follows each workout's estimated one-rep max, so different rep counts compare`() {
        val logs = listOf(
            workout(friday, bench, sets(100.0 to 1, 90.0 to 6)),
            workout(friday.minusDays(7), bench, sets(90.0 to 5)),
            workout(friday.minusDays(14), bench, sets(80.0 to 5, done = false)),
            workout(friday.minusDays(3), bench, sets(95.0 to 3), finished = false),
        )
        val progress = progression(logs, bench)
        assertEquals(ProgressMetric.OneRepMax, progress.metric)
        assertEquals(listOf(105.0, 108.0), progress.points.map { it.value })
        assertEquals(listOf(friday.minusDays(7), friday), progress.points.map { it.at.atZone(zone).toLocalDate() })
        assertEquals(90.0 to 6, progress.points.last().set.let { it.weight to it.reps })
    }

    @Test
    fun `bodyweight work follows reps until extra weight goes on, holds follow time, and help should go down`() {
        val reps = listOf(workout(friday.minusDays(7), pullUp, sets(null to 8)), workout(friday, pullUp, sets(null to 10, null to 7)))
        assertEquals(ProgressMetric.Reps to listOf(8.0, 10.0), progression(reps, pullUp).let { it.metric to it.points.map { p -> p.value } })

        val weighted = reps + workout(friday.plusDays(3), pullUp, sets(10.0 to 5, 5.0 to 8))
        assertEquals(ProgressMetric.Weight to listOf(10.0), progression(weighted, pullUp).let { it.metric to it.points.map { p -> p.value } })

        val holds = listOf(workout(friday, plank, listOf(WorkoutSet(nextId++, 0, 0, seconds = 60, done = true), WorkoutSet(nextId++, 0, 1, seconds = 90, done = true))))
        assertEquals(ProgressMetric.Time to listOf(90.0), progression(holds, plank).let { it.metric to it.points.map { p -> p.value } })

        val helped = listOf(workout(friday.minusDays(7), assisted, sets(30.0 to 8, 20.0 to 6)), workout(friday, assisted, sets(null to 3)))
        assertEquals(ProgressMetric.Assistance to listOf(20.0, 0.0), progression(helped, assisted).let { it.metric to it.points.map { p -> p.value } })
    }

    @Test
    fun `records come from beating every earlier workout, not from doing something the first time`() {
        val first = workout(friday.minusDays(14), bench, sets(80.0 to 5))
        val second = workout(friday.minusDays(7), bench, sets(90.0 to 3, 85.0 to 3))
        val today = workout(friday, bench, sets(85.0 to 8, 90.0 to 2))
        val logs = listOf(first, second, today)

        assertEquals(emptyList<Record>(), newRecords(first, logs))
        // 90 × 3 is a heavier weight and a better estimated max than 80 × 5.
        assertEquals(listOf(ProgressMetric.OneRepMax to 99.0, ProgressMetric.Weight to 90.0), newRecords(second, logs).map { it.metric to Math.round(it.value).toDouble() })
        // 85 × 8 beats the estimated max; 90 kg only ties the heaviest weight, so that's no record.
        val records = newRecords(today, logs)
        assertEquals(listOf(ProgressMetric.OneRepMax), records.map { it.metric })
        assertEquals(99.0, records.single().previous!!, 0.01)
        assertEquals(85.0 to 8, records.single().set.let { it.weight to it.reps })

        val standing = records(logs, bench)
        assertEquals(listOf(ProgressMetric.OneRepMax to friday, ProgressMetric.Weight to friday.minusDays(7)), standing.map { it.metric to it.at.atZone(zone).toLocalDate() })
    }

    @Test
    fun `workouts are counted by week, and averaged over the finished weeks since the first`() {
        val logs = listOf(
            workout(friday.minusWeeks(2), bench, sets(80.0 to 5)),
            workout(friday.minusWeeks(1), bench, sets(80.0 to 5)),
            workout(friday.minusWeeks(1).minusDays(2), bench, sets(80.0 to 5)),
            workout(friday, bench, sets(80.0 to 5)),
        )
        val weeks = weeklyWorkouts(logs, friday, DayOfWeek.MONDAY, zone, weeks = 4)
        assertEquals(listOf(0, 1, 2, 1), weeks.map { it.workouts })
        assertEquals(friday.with(DayOfWeek.MONDAY), weeks.last().start)
        assertEquals(1.5, averagePerWeek(weeks)!!, 0.01)
        assertNull(averagePerWeek(weeklyWorkouts(logs.takeLast(1), friday, DayOfWeek.MONDAY, zone, weeks = 4)))
    }
}
