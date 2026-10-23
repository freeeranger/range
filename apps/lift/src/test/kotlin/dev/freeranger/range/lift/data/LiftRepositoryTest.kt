package dev.freeranger.range.lift.data

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.freeranger.range.lift.ui.common.LiftFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
        assertEquals(BuiltInExercises.all.map { it.first }.sorted(), keys.sorted())
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
    fun `finishing keeps only ticked sets, and the next workout starts from them`() = runTest {
        val bench = bench()
        val squat = repository.exercises.first().first { it.key == "squat" }.id
        val monday = repository.start()
        repository.addToWorkout(monday, bench)
        repository.addToWorkout(monday, squat)
        val entry = log(monday).exercises.first().entry.id
        repository.updateSet(log(monday).exercises.first().sets.single().copy(weight = 80.0, reps = 5, done = true))
        repository.addSet(entry)
        repository.addSet(entry)
        val sets = log(monday).exercises.first().sets
        assertEquals(listOf(80.0, 80.0, 80.0), sets.map { it.weight })
        repository.updateSet(sets[1].copy(reps = 4, done = true))
        repository.finish(monday)

        val finished = log(monday)
        assertEquals(listOf(bench), finished.exercises.map { it.exercise.id })
        assertEquals(listOf(5, 4), finished.exercises.single().sets.map { it.reps })
        assertEquals(80.0 * 9, finished.volume, 0.001)

        now = now.plusSeconds(3 * 24 * 3600)
        val thursday = repository.start()
        repository.addToWorkout(thursday, bench)
        val prefilled = log(thursday).exercises.single().sets
        assertEquals(listOf(80.0 to 5, 80.0 to 4), prefilled.map { it.weight to it.reps })
        assertTrue(prefilled.none { it.done })

        val logs = repository.logs.first()
        assertEquals(finished.exercises.single().sets, previousSets(logs, bench, log(thursday).workout.startedAt))
        assertEquals(5, bestSets(logs)[bench]!!.reps)
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
