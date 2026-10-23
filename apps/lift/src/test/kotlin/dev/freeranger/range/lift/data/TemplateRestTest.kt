package dev.freeranger.range.lift.data

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Templates, the rest stopwatch and notes, on the real Room database in memory. */
@RunWith(RobolectricTestRunner::class)
class TemplateRestTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LiftDatabase::class.java)
        .setDriver(AndroidSQLiteDriver())
        .build()
    private var now: Instant = Instant.parse("2026-10-20T17:00:00Z")
    private val stopwatch = FakeStopwatch()
    private val repository = LiftRepository(database.lift(), stopwatch) { Clock.fixed(now, ZoneOffset.UTC) }

    @After
    fun close() = database.close()

    private suspend fun log(id: Long) = repository.log(id).first()!!
    private suspend fun exercise(key: String) = repository.exercises.first().first { it.key == key }

    /** Remembers what the phone would have been told. */
    private class FakeStopwatch : RestStopwatch {
        var since: Instant? = null
        var label: String? = null
        override fun show(workout: Workout, label: String) {
            since = workout.lastSetAt
            this.label = label
        }
        override fun cancel() {
            since = null
        }
    }

    private suspend fun tickAll(workoutId: Long) {
        log(workoutId).exercises.flatMap { it.sets }.forEach { repository.updateSet(it.copy(weight = 60.0, reps = 8, done = true)) }
    }

    @Test
    fun `a workout saved as a template starts again with its exercises and set counts`() = runTest {
        repository.addBuiltInExercises()
        val bench = exercise("bench_press")
        val dip = exercise("dip")
        val monday = repository.start()
        repository.addToWorkout(monday, bench.id)
        repository.addToWorkout(monday, dip.id)
        val benchEntry = log(monday).exercises.first().entry.id
        repository.addSet(benchEntry)
        repository.addSet(benchEntry)
        tickAll(monday)
        repository.finish(monday)

        val templateId = repository.saveTemplate(monday, "  Push ")!!
        val template = repository.templates.first().single()
        assertEquals("Push", template.template.name)
        assertEquals(listOf(bench to 3, dip to 1), template.exercises)
        assertEquals(templateId, log(monday).workout.templateId)
        assertTrue(template.matches(log(monday)))

        now = now.plusSeconds(3 * 24 * 3600)
        val thursday = repository.startTemplate(templateId)!!
        val started = log(thursday)
        assertEquals("Push", started.workout.name)
        assertEquals(templateId, started.workout.templateId)
        assertEquals(listOf(3, 1), started.exercises.map { it.sets.size })
        assertTrue(started.exercises.flatMap { it.sets }.none { it.done || it.weight != null })
        // One workout at a time: starting another hands back this one.
        assertEquals(thursday, repository.startTemplate(templateId))

        repository.deleteSet(started.exercises.first().sets.last().id)
        tickAll(thursday)
        repository.finish(thursday)
        assertFalse(repository.templates.first().single().matches(log(thursday)))
        repository.updateTemplate(templateId, thursday)
        assertEquals(listOf(bench to 2, dip to 1), repository.templates.first().single().exercises)

        repository.renameTemplate(templateId, "Push day")
        assertEquals("Push day", repository.templates.first().single().template.name)
        repository.deleteTemplate(templateId)
        assertEquals(emptyList<WorkoutTemplate>(), repository.templates.first())
        assertEquals(2, repository.logs.first().size)
    }

    @Test
    fun `the rest stopwatch counts from the last set and stops with the workout`() = runTest {
        val workout = repository.start()
        assertNull(log(workout).workout.sinceLastSet(now))
        repository.setDone(workout, "Bench press")
        assertEquals(now, stopwatch.since)
        assertEquals("Bench press", stopwatch.label)

        now = now.plusSeconds(95)
        assertEquals(95, log(workout).workout.sinceLastSet(now)!!.seconds)
        repository.setDone(workout, "Curl")
        assertEquals(0, log(workout).workout.sinceLastSet(now)!!.seconds)
        assertEquals("Curl", stopwatch.label)

        repository.finish(workout)
        assertNull(stopwatch.since)
        assertNull(log(workout).workout.sinceLastSet(now))
        repository.setDone(workout, "After finishing")
        assertNull(stopwatch.since)
    }

    @Test
    fun `notes are kept trimmed`() = runTest {
        val workout = repository.start()
        repository.setNotes(workout, "  Felt strong.\n")
        assertEquals("Felt strong.", log(workout).workout.notes)
    }
}
