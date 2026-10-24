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

    /** Every built-in exercise, as libraries had them before most were retired. */
    private suspend fun addAllBuiltIns() = database.lift().insertBuiltIn(BuiltInExercises.all.map { it.toExercise() })

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
        addAllBuiltIns()
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
        assertEquals(listOf(bench to 3, dip to 1), template.exercises.map { it.exercise to it.sets })
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
        assertEquals(listOf(bench to 2, dip to 1), repository.templates.first().single().exercises.map { it.exercise to it.sets })

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

    @Test
    fun `a template is built up and changed exercise by exercise`() = runTest {
        addAllBuiltIns()
        val bench = exercise("bench_press")
        val squat = exercise("squat")
        val row = exercise("barbell_row")
        val id = repository.createTemplate(" Full body ")
        repository.pick(PickTarget.IntoTemplate(id), listOf(bench.id, squat.id))
        fun plan(template: WorkoutTemplate) = template.exercises.map { it.exercise.key to it.sets }
        suspend fun template() = repository.templates.first().single()
        assertEquals("Full body", template().template.name)
        // Never done: the usual three sets each.
        assertEquals(listOf("bench_press" to 3, "squat" to 3), plan(template()))

        val (benchEntry, squatEntry) = template().exercises.map { it.entry }
        repository.setTemplateSets(benchEntry.id, 5)
        repository.setTemplateSets(squatEntry.id, 0)
        repository.pick(PickTarget.SwapInTemplate(squatEntry.id), listOf(row.id))
        assertEquals(listOf("bench_press" to 5, "barbell_row" to 1), plan(template()))

        repository.reorderTemplate(listOf(squatEntry.id, benchEntry.id))
        assertEquals(listOf("barbell_row" to 1, "bench_press" to 5), plan(template()))
        val removed = template().exercises.first().entry
        repository.removeFromTemplate(removed.id)
        assertEquals(listOf("bench_press" to 5), plan(template()))
        repository.restoreTemplateExercise(removed)
        assertEquals(listOf("barbell_row" to 1, "bench_press" to 5), plan(template()))

        val workout = repository.startTemplate(id)!!
        assertEquals(listOf(row.id to 1, bench.id to 5), log(workout).exercises.map { it.exercise.id to it.sets.size })
    }

    @Test
    fun `an exercise swapped in a workout keeps its place and number of sets, emptied`() = runTest {
        addAllBuiltIns()
        val workout = repository.start()
        repository.pick(PickTarget.IntoWorkout(workout), listOf(exercise("bench_press").id, exercise("squat").id))
        val bench = log(workout).exercises.first()
        repository.addSet(bench.entry.id)
        repository.updateSet(log(workout).exercises.first().sets.first().copy(weight = 60.0, reps = 5, done = true))
        val dumbbell = exercise("dumbbell_bench_press")
        repository.pick(PickTarget.SwapInWorkout(bench.entry.id), listOf(dumbbell.id))
        val swapped = log(workout).exercises.first()
        assertEquals(bench.entry.id to dumbbell.id, swapped.entry.id to swapped.exercise.id)
        assertEquals(2, swapped.sets.size)
        assertTrue(swapped.sets.none { it.done || it.weight != null })
        assertEquals("squat", log(workout).exercises.last().exercise.key)
    }

    @Test
    fun `a finished workout's times can be corrected, never ending before it starts`() = runTest {
        val workout = repository.start()
        val start = now
        repository.setTimes(workout, start.minusSeconds(60), start)
        assertEquals(start, log(workout).workout.startedAt)

        now = now.plusSeconds(3600)
        repository.finish(workout)
        repository.setTimes(workout, start.minusSeconds(600), start.plusSeconds(2400))
        assertEquals(start.minusSeconds(600) to start.plusSeconds(2400), log(workout).workout.let { it.startedAt to it.finishedAt })
        repository.setTimes(workout, start, start.minusSeconds(60))
        assertEquals(start to start, log(workout).workout.let { it.startedAt to it.finishedAt })
    }

    @Test
    fun `a finished workout repeats with its name, exercises and ticked set counts, unless one is in progress`() = runTest {
        addAllBuiltIns()
        val monday = repository.start()
        repository.rename(monday, "Push")
        repository.pick(PickTarget.IntoWorkout(monday), listOf(exercise("bench_press").id, exercise("dip").id))
        val bench = log(monday).exercises.first().entry.id
        repository.addSet(bench)
        repository.addSet(bench)
        tickAll(monday)
        repository.deleteSet(log(monday).exercises.last().sets.single().id)
        repository.finish(monday)

        val other = repository.start()
        assertNull(repository.repeat(monday))
        repository.delete(other)

        now = now.plusSeconds(3 * 24 * 3600)
        val again = log(repository.repeat(monday)!!)
        assertEquals("Push", again.workout.name)
        assertEquals(now, again.workout.startedAt)
        assertEquals(listOf("bench_press" to 3), again.exercises.map { it.exercise.key to it.sets.size })
        assertTrue(again.exercises.flatMap { it.sets }.none { it.done || it.weight != null })
    }
}
