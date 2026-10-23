package dev.freeranger.range.lift.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

class ProgressTest {
    private val friday = LocalDate.of(2026, 10, 23)
    private val zone = ZoneOffset.UTC
    private val bench = Exercise(id = 1, muscle = Muscle.Chest)
    private val plank = Exercise(id = 2, muscle = Muscle.Core, measure = Measure.Time, load = Load.Bodyweight)
    private val squat = Exercise(id = 3, muscle = Muscle.Legs)
    private var nextId = 1L

    private fun workout(day: LocalDate, vararg lifts: Pair<Exercise, List<WorkoutSet>>, finished: Boolean = true): WorkoutLog {
        val workout = Workout(nextId++, "", day.atTime(17, 0).toInstant(zone), if (finished) day.atTime(18, 0).toInstant(zone) else null)
        return WorkoutLog(workout, lifts.mapIndexed { i, (exercise, sets) -> LoggedExercise(WorkoutExercise(nextId++, workout.id, exercise.id, i), exercise, sets) })
    }

    private fun sets(vararg sets: Pair<Double?, Int>, done: Boolean = true) =
        sets.mapIndexed { i, (kg, reps) -> WorkoutSet(nextId++, 0, i, kg, reps, done) }

    @Test
    fun `weekly totals cover every week up to this one, empty weeks included, unfinished workouts left out`() {
        val logs = listOf(
            workout(friday, bench to sets(100.0 to 5, 100.0 to 5)),
            workout(friday.minusDays(2), bench to sets(80.0 to 10) + sets(80.0 to 10, done = false)),
            workout(friday, bench to sets(200.0 to 5), finished = false),
            workout(friday.minusWeeks(2), plank to listOf(WorkoutSet(nextId++, 0, 0, seconds = 60, done = true))),
        )
        val weeks = weeklyTotals(logs, friday, DayOfWeek.MONDAY, zone, weeks = 3)
        assertEquals(listOf(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 19)), weeks.map { it.start })
        assertEquals(WeekTotals(LocalDate.of(2026, 10, 19), workouts = 2, sets = 3, volume = 1800.0), weeks.last())
        assertEquals(WeekTotals(LocalDate.of(2026, 10, 12), 0, 0, 0.0), weeks[1])
        assertEquals(WeekTotals(LocalDate.of(2026, 10, 5), 1, 1, 0.0), weeks[0])
    }

    @Test
    fun `the one-rep max chart follows each workout's best set, for the lifts done most`() {
        val logs = listOf(
            workout(friday, bench to sets(100.0 to 1, 90.0 to 6), squat to sets(120.0 to 5)),
            workout(friday.minusDays(7), bench to sets(90.0 to 5), plank to sets(null to 0)),
            workout(friday.minusDays(3), squat to sets(110.0 to 5)),
            workout(friday.minusDays(14), bench to sets(80.0 to 5, done = false)),
        )
        val history = oneRepMaxHistory(logs, bench.id)
        assertEquals(listOf(friday.minusDays(7), friday), history.map { it.at.atZone(zone).toLocalDate() })
        assertEquals(listOf(105.0, 108.0), history.map { it.kg })
        // Two workouts each, so the latest decides; timed and unfinished work isn't charted.
        assertEquals(listOf(bench, squat), progressExercises(logs))
    }

    @Test
    fun `sets per muscle group count finished sets since the date, every group shown`() {
        val logs = listOf(
            workout(friday, bench to sets(100.0 to 5, 100.0 to 5) + sets(100.0 to 5, done = false), squat to sets(120.0 to 5)),
            workout(friday.minusDays(40), squat to sets(120.0 to 5, 120.0 to 5, 120.0 to 5)),
        )
        val counts = setsPerMuscle(logs, friday.minusDays(30).atStartOfDay().toInstant(zone))
        assertEquals(Muscle.entries.size, counts.size)
        assertEquals(listOf(Muscle.Chest to 2, Muscle.Legs to 1), counts.take(2))
        assertEquals(0, counts.last().second)
    }
}
