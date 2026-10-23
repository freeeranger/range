package dev.freeranger.range.lift.data

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.freeranger.range.lift.ui.common.LiftFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Runs the real Room database in memory. */
@RunWith(RobolectricTestRunner::class)
class LiftRepositoryTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LiftDatabase::class.java)
        .setDriver(AndroidSQLiteDriver())
        .build()
    private var now: Instant = Instant.parse("2026-10-20T17:00:00Z")
    private val repository = LiftRepository(database.lift()) { Clock.fixed(now, ZoneOffset.UTC) }

    @After
    fun close() = database.close()

    private suspend fun log(id: Long) = repository.log(id).first()!!

    private suspend fun bench(): Long {
        repository.addBuiltInExercises()
        return repository.exercises.first().first { it.key == "bench_press" }.id
    }

    @Test
    fun `built-in exercises are added once, and each has a name`() = runTest {
        repository.addBuiltInExercises()
        repository.addBuiltInExercises()
        val keys = repository.exercises.first().mapNotNull { it.key }
        assertEquals(BuiltInExercises.all.map { it.key }.sorted(), keys.sorted())
        assertEquals(keys.toSet(), LiftFormat.BUILT_IN_NAMES.keys)
    }

    @Test
    fun `only one workout runs at a time`() = runTest {
        val first = repository.start()
        assertEquals(first, repository.start())
        repository.addToWorkout(first, bench())
        repository.updateSet(log(first).exercises.single().sets.single().copy(weight = 60.0, reps = 8, done = true))
        repository.finish(first)
        assertNotEquals(first, repository.start())
    }

    @Test
    fun `finishing keeps only ticked sets, and the next workout starts with as many empty ones`() = runTest {
        val bench = bench()
        val squat = repository.exercises.first().first { it.key == "squat" }.id
        val monday = repository.start()
        repository.addToWorkout(monday, bench)
        repository.addToWorkout(monday, squat)
        val entry = log(monday).exercises.first().entry.id
        repository.addSet(entry)
        repository.addSet(entry)
        val sets = log(monday).exercises.first().sets
        assertEquals(3, sets.size)
        assertTrue(sets.all { it.weight == null && it.reps == null })
        repository.updateSet(sets[0].copy(weight = 80.0, reps = 5, done = true))
        repository.updateSet(sets[1].copy(weight = 80.0, reps = 4, done = true))
        repository.finish(monday)

        val finished = log(monday)
        assertEquals(listOf(bench), finished.exercises.map { it.exercise.id })
        assertEquals(listOf(5, 4), finished.exercises.single().sets.map { it.reps })
        assertEquals(80.0 * 9, finished.volume, 0.001)

        now = now.plusSeconds(3 * 24 * 3600)
        val thursday = repository.start()
        repository.addToWorkout(thursday, bench)
        val fresh = log(thursday).exercises.single().sets
        assertEquals(2, fresh.size)
        assertTrue(fresh.none { it.done || it.weight != null || it.reps != null })

        val logs = repository.logs.first()
        val previous = previousSets(logs, bench, log(thursday).workout.startedAt)
        assertEquals(finished.exercises.single().sets, previous)
        assertEquals(previous, placeholders(fresh, previous))
        assertEquals(5, bestSets(logs)[bench]!!.reps)
    }

    @Test
    fun `an empty set shows last time's set, else the one above, and ticking takes those numbers`() {
        fun set(position: Int, kg: Double? = null, reps: Int? = null) = WorkoutSet(workoutExerciseId = 1, position = position, weight = kg, reps = reps)
        val last = listOf(set(0, 60.0, 8))
        val sets = listOf(set(0), set(1, 65.0, 6), set(2), set(3))
        val shown = placeholders(sets, last)
        assertEquals(listOf(60.0 to 8, 60.0 to 8, 65.0 to 6, 65.0 to 6), shown.map { it?.weight to it?.reps })

        val bench = Exercise(muscle = Muscle.Chest)
        val filled = sets[0].copy(reps = 10).filledFrom(shown[0])
        assertEquals(60.0 to 10, filled.weight to filled.reps)
        assertTrue(filled.isComplete(bench))
        assertFalse(set(0, 60.0).isComplete(bench))
        assertTrue(WorkoutSet(workoutExerciseId = 1, position = 0, seconds = 45).isComplete(bench.copy(measure = Measure.Time)))
    }

    @Test
    fun `exercises can be edited, removed and put back, and removed ones stay in past workouts`() = runTest {
        repository.addBuiltInExercises()
        val pullUp = repository.exercises.first().first { it.key == "pull_up" }
        assertEquals(Load.ExtraWeight, pullUp.load)
        repository.updateExercise(pullUp.copy(name = "  Chin-up ", load = Load.Bodyweight, key = "something else", archived = true))
        val edited = repository.exercises.first().first { it.id == pullUp.id }
        assertEquals(pullUp.copy(name = "Chin-up", load = Load.Bodyweight), edited)

        val workout = repository.start()
        repository.addToWorkout(workout, pullUp.id)
        repository.updateSet(log(workout).exercises.single().sets.single().copy(reps = 10, done = true))
        repository.finish(workout)
        repository.setRemoved(pullUp.id, true)
        repository.addBuiltInExercises()
        assertTrue(repository.exercises.first().single { it.key == "pull_up" }.archived)
        assertEquals(pullUp.id, log(workout).exercises.single().exercise.id)
        repository.setRemoved(pullUp.id, false)
        assertFalse(repository.exercises.first().single { it.key == "pull_up" }.archived)

        val own = repository.addExercise(Exercise(id = 5, key = "bench_press", name = " Landmine press ", muscle = Muscle.Shoulders, perSide = true))
        val added = repository.exercises.first().single { it.id == own }
        assertEquals(Exercise(id = own, name = "Landmine press", muscle = Muscle.Shoulders, perSide = true), added)
    }

    @Test
    fun `a workout's exercises can be put in a new order`() = runTest {
        val bench = bench()
        val squat = repository.exercises.first().first { it.key == "squat" }.id
        val plank = repository.exercises.first().first { it.key == "plank" }.id
        val workout = repository.start()
        listOf(bench, squat, plank).forEach { repository.addToWorkout(workout, it) }
        val entries = log(workout).exercises.map { it.entry.id }
        repository.reorder(listOf(entries[2], entries[0], entries[1]))
        assertEquals(listOf(plank, bench, squat), log(workout).exercises.map { it.exercise.id })
    }

    @Test
    fun `volume counts only weight lifted, both sides, and best sets follow each exercise's measure`() {
        fun set(kg: Double? = null, reps: Int? = null, seconds: Int? = null) =
            WorkoutSet(workoutExerciseId = 1, position = 0, weight = kg, reps = reps, seconds = seconds, done = true)
        fun logged(exercise: Exercise, vararg sets: WorkoutSet) = LoggedExercise(WorkoutExercise(1, 1, exercise.id, 0), exercise, sets.toList())

        val row = Exercise(muscle = Muscle.Back, perSide = true)
        assertEquals(2 * 30.0 * 10, logged(row, set(30.0, 10)).volume, 0.001)
        assertEquals(10.0 * 5, logged(Exercise(muscle = Muscle.Back, load = Load.ExtraWeight), set(10.0, 5), set(reps = 12)).volume, 0.001)
        assertEquals(0.0, logged(Exercise(muscle = Muscle.Back, load = Load.Assisted), set(20.0, 8)).volume, 0.001)
        assertEquals(0.0, logged(Exercise(muscle = Muscle.Core, measure = Measure.Time), set(20.0, seconds = 60)).volume, 0.001)

        val assisted = logged(Exercise(muscle = Muscle.Back, load = Load.Assisted), set(30.0, 8), set(20.0, 6), set(20.0, 5))
        assertEquals(set(20.0, 6), assisted.best)
        val plank = logged(Exercise(muscle = Muscle.Core, measure = Measure.Time, load = Load.Bodyweight), set(seconds = 60), set(seconds = 90))
        assertEquals(90, plank.best!!.seconds)
        val bench = logged(Exercise(muscle = Muscle.Chest), set(100.0, 1), set(90.0, 6))
        assertEquals(set(90.0, 6), bench.best)
    }

    @Test
    fun `deleting a workout removes its sets, and a bodyweight set has no estimated max`() = runTest {
        val id = repository.start()
        repository.addToWorkout(id, bench())
        repository.delete(id)
        assertEquals(emptyList<WorkoutLog>(), repository.logs.first())
        assertEquals(null, WorkoutSet(workoutExerciseId = 0, position = 0, reps = 12).estimatedMax)
        assertEquals(100.0, WorkoutSet(workoutExerciseId = 0, position = 0, weight = 100.0, reps = 1).estimatedMax!!, 0.001)
    }
}
