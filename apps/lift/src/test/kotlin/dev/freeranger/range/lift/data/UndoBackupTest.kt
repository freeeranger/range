package dev.freeranger.range.lift.data

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Undoing deletes, tidying an edited workout, backups and recent exercises, on the real Room database in memory. */
@RunWith(RobolectricTestRunner::class)
class UndoBackupTest {
    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LiftDatabase::class.java)
        .setDriver(AndroidSQLiteDriver())
        .build()
    private val database = database()
    private var now: Instant = Instant.parse("2026-10-20T17:00:00Z")
    private val repository = LiftRepository(database.lift()) { Clock.fixed(now, ZoneOffset.UTC) }

    @After
    fun close() = database.close()

    private suspend fun log(id: Long) = repository.log(id).first()!!
    private suspend fun exercise(key: String) = repository.exercises.first().first { it.key == key }

    /** A finished workout of bench press (two sets) and squat (one). */
    private suspend fun workout(): Long {
        repository.addBuiltInExercises()
        val id = repository.start()
        repository.addToWorkout(id, exercise("bench_press").id)
        repository.addToWorkout(id, exercise("squat").id)
        repository.addSet(log(id).exercises.first().entry.id)
        log(id).exercises.flatMap { it.sets }.forEachIndexed { i, set -> repository.updateSet(set.copy(weight = 80.0 + i, reps = 5, done = true)) }
        repository.finish(id)
        return id
    }

    @Test
    fun `a deleted set and a removed exercise come back as they were`() = runTest {
        val id = workout()
        val before = log(id)
        val set = before.exercises.first().sets.first()
        repository.deleteSet(set.id)
        assertEquals(1, log(id).exercises.first().sets.size)
        repository.restoreSet(set)
        assertEquals(before, log(id))

        val squat = before.exercises.last()
        repository.removeFromWorkout(squat.entry.id)
        assertEquals(1, log(id).exercises.size)
        repository.restoreExercise(squat)
        assertEquals(before, log(id))
    }

    @Test
    fun `editing a finished workout keeps only what's ticked off`() = runTest {
        val id = workout()
        val bench = log(id).exercises.first()
        repository.updateSet(bench.sets.first().copy(weight = 85.0))
        repository.addSet(bench.entry.id)
        repository.addToWorkout(id, exercise("deadlift").id)
        repository.tidy(id)
        val edited = log(id)
        assertEquals(listOf(85.0, 81.0), edited.exercises.first().sets.map { it.weight })
        assertEquals(2, edited.exercises.size)
        assertTrue(edited.workout.isFinished)
    }

    @Test
    fun `a backup restores everything exactly, and anything else is turned away untouched`() = runTest {
        val id = workout()
        repository.setNotes(id, "Good one")
        repository.saveTemplate(id, "Push")
        val own = repository.addExercise(Exercise(name = "Landmine press", muscle = Muscle.Shoulders, perSide = true))
        repository.setRemoved(exercise("dip").id, true)
        val text = repository.exportBackup()

        val other = database()
        val restored = LiftRepository(other.lift())
        assertTrue(restored.importBackup(text))
        assertEquals(repository.logs.first(), restored.logs.first())
        assertEquals(repository.templates.first(), restored.templates.first())
        assertEquals(repository.exercises.first().sortedBy { it.id }, restored.exercises.first().sortedBy { it.id })
        assertTrue(restored.exercises.first().any { it.id == own && it.perSide })

        assertFalse(restored.importBackup("{\"hello\": 1}"))
        assertFalse(restored.importBackup("not json"))
        assertEquals(repository.logs.first(), restored.logs.first())
        other.close()
    }

    @Test
    fun `recent exercises are the latest done, newest first, without removed ones`() = runTest {
        val first = workout()
        now = now.plusSeconds(86400)
        val second = repository.start()
        repository.addToWorkout(second, exercise("deadlift").id)
        repository.addToWorkout(second, exercise("bench_press").id)
        log(second).exercises.flatMap { it.sets }.forEach { repository.updateSet(it.copy(weight = 100.0, reps = 3, done = true)) }
        repository.finish(second)
        repository.setRemoved(exercise("squat").id, true)

        assertEquals(listOf("deadlift", "bench_press"), recentExercises(repository.logs.first()).map { it.key })
        assertEquals(first, log(first).workout.id)
    }
}
