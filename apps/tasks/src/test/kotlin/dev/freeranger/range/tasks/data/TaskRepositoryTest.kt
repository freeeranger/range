package dev.freeranger.range.tasks.data

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/** Runs the real Room database in memory, with reminders recorded instead of scheduled. */
@RunWith(RobolectricTestRunner::class)
class TaskRepositoryTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TasksDatabase::class.java)
        .setDriver(AndroidSQLiteDriver())
        .build()
    private val reminders = RecordingReminders()
    private val friday = LocalDate.of(2026, 10, 23)
    private var now: LocalDateTime = friday.atTime(7, 0)
    private val repository = TaskRepository(database.tasks(), reminders) { Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC) }

    @After
    fun close() = database.close()

    private val vitamins = Schedule(friday, listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), Repeat(anchor = friday))

    @Test
    fun `ticking, unticking, undoing and reopening a repeating task never makes a second row`() = runTest {
        val id = repository.add("Take vitamins", schedule = vitamins)

        now = friday.atTime(8, 5)
        val before = repository.check(id)!!
        repository.restore(before)
        repository.check(id)
        repository.uncheck(id)
        repository.check(id)
        now = friday.atTime(20, 5)
        repository.check(id)
        assertEquals(friday.plusDays(1), repository.get(id)!!.dueDate)

        repository.reopen(id)
        assertEquals(1, repository.get(id)!!.doneCount)
        repository.check(id)

        val tasks = repository.tasks.first()
        assertEquals(1, tasks.size)
        assertEquals(friday.plusDays(1), tasks.single().dueDate)
    }

    @Test
    fun `undo puts the task back exactly as it was`() = runTest {
        val id = repository.add("Pay rent", schedule = Schedule(friday))
        val before = repository.check(id)!!
        assertNotNull(repository.get(id)!!.completedAt)
        repository.restore(before)
        assertEquals(before, repository.get(id))
    }

    @Test
    fun `tomorrow's occurrence can't be ticked off today`() = runTest {
        val id = repository.add("Take vitamins", schedule = vitamins)
        now = friday.atTime(21, 0)
        repository.check(id)
        repository.check(id)
        val tomorrow = repository.get(id)!!
        assertEquals(friday.plusDays(1), tomorrow.dueDate)

        repository.check(id)
        assertEquals(tomorrow, repository.get(id))
    }

    @Test
    fun `the notification's Complete ticks once, and ignores stale days`() = runTest {
        val id = repository.add("Take vitamins", schedule = vitamins)
        now = friday.atTime(8, 0)
        repository.completeFromReminder(id, friday)
        assertEquals(1, repository.get(id)!!.doneCount)

        repository.completeFromReminder(id, friday.minusDays(1))
        assertEquals(1, repository.get(id)!!.doneCount)
    }

    @Test
    fun `completing a one-off task completes it`() = runTest {
        val id = repository.add("Pay rent", schedule = Schedule(friday))
        repository.check(id)
        assertNotNull(repository.get(id)!!.completedAt)
        assertEquals(1, repository.tasks.first().size)
    }

    @Test
    fun `finished days are logged, and undo and reopen take them back out`() = runTest {
        val id = repository.add("Take vitamins", schedule = vitamins)
        suspend fun days() = repository.completions.first().map { it.day }

        now = friday.atTime(8, 5)
        repository.check(id)
        assertEquals(emptyList<LocalDate>(), days())

        now = friday.atTime(20, 5)
        val before = repository.check(id)!!
        assertEquals(listOf(friday), days())
        repository.restore(before)
        assertEquals(emptyList<LocalDate>(), days())

        repository.check(id)
        repository.reopen(id)
        assertEquals(emptyList<LocalDate>(), days())

        repository.check(id)
        now = friday.plusDays(1).atTime(20, 5)
        repository.check(id)
        repository.check(id)
        assertEquals(listOf(friday, friday.plusDays(1)), days())

        // Rescheduling starts the task over but doesn't rewrite what was done.
        repository.setSchedule(id, null)
        assertEquals(listOf(friday, friday.plusDays(1)), days())
    }

    @Test
    fun `ticking off yesterday's repeat late logs it under yesterday`() = runTest {
        val id = repository.add("Take vitamins", schedule = vitamins)
        now = friday.plusDays(1).atTime(9, 0)
        repository.check(id)
        repository.check(id)
        assertEquals(listOf(friday), repository.completions.first().map { it.day })
        assertEquals(friday.plusDays(1), repository.get(id)!!.dueDate)
    }

    @Test
    fun `a gone day can be ticked off from its page, and undone`() = runTest {
        val id = repository.add("Take vitamins", schedule = vitamins)
        now = friday.plusDays(2).atTime(21, 0)
        // Friday's ticked late, skipping Saturday.
        repository.check(id)
        repository.check(id)
        assertEquals(friday.plusDays(2), repository.get(id)!!.dueDate)

        val saturday = repository.backfill(id, friday.plusDays(1))!!
        assertEquals(listOf(friday, friday.plusDays(1)), repository.completions.first().map { it.day }.sorted())
        // Not twice, not a day it doesn't fall on, and not the day it's on now: that's an ordinary tick.
        assertEquals(null, repository.backfill(id, friday.plusDays(1)))
        assertEquals(null, repository.backfill(id, friday.minusDays(1)))
        assertEquals(null, repository.backfill(id, friday.plusDays(2)))
        assertEquals(friday.plusDays(2), repository.get(id)!!.dueDate)

        repository.unbackfill(saturday)
        assertEquals(listOf(friday), repository.completions.first().map { it.day })
    }

    @Test
    fun `a late tick logged under the wrong day by an older version is moved, not doubled`() = runTest {
        val id = repository.add("Take vitamins", schedule = vitamins)
        val saturday = friday.plusDays(1)
        now = saturday.atTime(9, 0)
        repository.check(id)
        repository.check(id)
        // As the older version logged it: under the day the late tick was made.
        val row = repository.completions.first().single()
        database.tasks().moveCompletion(row.id, saturday)

        val moved = repository.backfill(id, friday)!!
        assertEquals(listOf(friday), repository.completions.first().map { it.day })
        repository.unbackfill(moved)
        assertEquals(listOf(saturday), repository.completions.first().map { it.day })
    }

    @Test
    fun `a one-off task ticked off from its gone day's page is logged under that day`() = runTest {
        val id = repository.add("Pay rent", schedule = Schedule(friday))
        now = friday.plusDays(3).atTime(9, 0)
        repository.check(id, on = friday)
        assertEquals(listOf(friday), repository.completions.first().map { it.day })
        val other = repository.add("Call bank", schedule = Schedule(friday))
        repository.check(other)
        assertEquals(friday.plusDays(3), repository.completions.first().single { it.taskId == other }.day)
    }

    @Test
    fun `deleting a task deletes its history`() = runTest {
        val id = repository.add("Pay rent", schedule = Schedule(friday))
        val other = repository.add("Buy oat milk")
        repository.check(id)
        repository.check(other)
        repository.delete(id)
        assertEquals(listOf(other), repository.completions.first().map { it.taskId })
    }

    @Test
    fun `a blank title edit keeps the old title`() = runTest {
        val id = repository.add("Pay rent")
        repository.editText(id, "   ", "before Friday")
        val task = repository.get(id)!!
        assertEquals("Pay rent", task.title)
        assertEquals("before Friday", task.details)
    }

    @Test
    fun `schedules survive the trip through the database`() = runTest {
        val repeat = Repeat(every = 2, unit = RepeatUnit.Week, weekdays = setOf(1, 5), end = RepeatEnd.After(4), anchor = friday)
        val id = repository.add("Run", schedule = Schedule(friday, listOf(LocalTime.of(18, 30), LocalTime.of(7, 15)), repeat))
        val task = repository.get(id)!!
        assertEquals(listOf(LocalTime.of(7, 15), LocalTime.of(18, 30)), task.times)
        assertEquals(repeat, task.repeat)
    }

    @Test
    fun `undoing a delete brings the task back with its history and reminder`() = runTest {
        val id = repository.add("Take vitamins", schedule = vitamins)
        now = friday.atTime(20, 5)
        repository.check(id)
        repository.check(id)
        val task = repository.get(id)!!
        val history = repository.completions.first()

        val deleted = repository.delete(id)!!
        assertEquals(emptyList<Task>(), repository.tasks.first())
        assertEquals(id, reminders.cancelled.last())

        repository.restore(deleted)
        assertEquals(task, repository.get(id))
        assertEquals(history, repository.completions.first())
        assertEquals(task, reminders.synced.last())
    }

    @Test
    fun `a backup restores everything exactly, and anything else is turned away untouched`() = runTest {
        val rent = repository.add("Pay rent", "before Friday", Schedule(friday))
        val vitaminsId = repository.add("Take vitamins", schedule = Schedule(friday, vitamins.times, vitamins.repeat!!.copy(end = RepeatEnd.After(10))))
        repository.add("Buy oat milk")
        now = friday.atTime(8, 5)
        repository.check(rent)
        repository.check(vitaminsId)
        val tasks = repository.tasks.first().sortedBy { it.id }
        val completions = repository.completions.first()
        val backup = repository.exportBackup()

        repository.delete(rent)
        repository.add("Something new")
        assertFalse(repository.importBackup("""{"format":2,"exercises":[],"workouts":[],"entries":[],"sets":[]}"""))
        assertFalse(repository.importBackup("not json"))
        assertEquals(3, repository.tasks.first().size)

        reminders.cancelled.clear()
        assertTrue(repository.importBackup(backup))
        assertEquals(tasks, repository.tasks.first().sortedBy { it.id })
        assertEquals(completions, repository.completions.first())
        // The task only in the replaced data has its reminder cleared.
        assertTrue(4L in reminders.cancelled)
    }

    private class RecordingReminders : Reminders {
        val synced = mutableListOf<Task>()
        val cancelled = mutableListOf<Long>()
        override suspend fun sync(task: Task) {
            synced += task
        }
        override fun cancel(taskId: Long) {
            cancelled += taskId
        }
    }
}
