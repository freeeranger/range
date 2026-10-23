package dev.freeranger.range.tasks.data

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    private class RecordingReminders : Reminders {
        override fun sync(task: Task) = Unit
        override fun cancel(taskId: Long) = Unit
    }
}
