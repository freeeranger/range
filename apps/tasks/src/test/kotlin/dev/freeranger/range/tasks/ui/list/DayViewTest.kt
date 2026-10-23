package dev.freeranger.range.tasks.ui.list

import dev.freeranger.range.tasks.data.Completion
import dev.freeranger.range.tasks.data.Repeat
import dev.freeranger.range.tasks.data.RepeatEnd
import dev.freeranger.range.tasks.data.RepeatUnit
import dev.freeranger.range.tasks.data.Schedule
import dev.freeranger.range.tasks.data.Task
import dev.freeranger.range.tasks.data.check
import dev.freeranger.range.tasks.data.withSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class DayViewTest {
    private val friday = LocalDate.of(2026, 10, 23)
    private val morning = LocalTime.of(8, 0)

    private fun task(id: Long, title: String, schedule: Schedule? = null) =
        Task(id = id, title = title, createdAt = Instant.EPOCH).withSchedule(schedule, friday.atStartOfDay())

    private val creatine = task(1, "Creatine", Schedule(friday, listOf(morning), Repeat(anchor = friday)))
    private val rent = task(2, "Pay rent", Schedule(friday.minusDays(1)))
    private val milk = task(3, "Buy oat milk")
    private val dentist = task(4, "Dentist", Schedule(friday.plusDays(3)))
    private val gym = task(5, "Gym", Schedule(friday, emptyList(), Repeat(unit = RepeatUnit.Week, weekdays = setOf(1, 3), anchor = friday)))
    private val all = listOf(creatine, rent, milk, dentist, gym)

    @Test
    fun `today shows overdue, today and undated tasks, and nothing from later days`() {
        val view = dayView(all, emptyList(), friday, friday)
        assertEquals(listOf(rent), view.overdue)
        assertEquals(listOf(creatine), view.due)
        assertEquals(listOf(milk), view.anytime)
    }

    private fun instant(at: LocalDateTime) = at.atZone(ZoneId.systemDefault()).toInstant()

    private fun ticked(task: Task, at: LocalDateTime) = Completion(taskId = task.id, day = at.toLocalDate(), at = instant(at))

    @Test
    fun `tomorrow's repeat of a task done today stays off today's list`() {
        val done = creatine.check(friday.atTime(8, 5), instant(friday.atTime(8, 5)))
        val view = dayView(listOf(done), listOf(ticked(done, friday.atTime(8, 5))), friday, friday)
        assertEquals(emptyList<Task>(), view.due)
        assertEquals(listOf(done), view.completed)
        assertEquals(listOf(done), dayView(listOf(done), emptyList(), friday.plusDays(1), friday).due)
    }

    @Test
    fun `today's Completed lists only what was ticked off today, latest first`() {
        val milkDone = milk.copy(completedAt = instant(friday.atTime(9, 0)))
        val rentDone = rent.copy(completedAt = instant(friday.minusDays(2).atTime(9, 0)))
        val dentistDone = dentist.copy(completedAt = instant(friday.atTime(11, 0)))
        val history = listOf(ticked(milkDone, friday.atTime(9, 0)), ticked(rentDone, friday.minusDays(2).atTime(9, 0)), ticked(dentistDone, friday.atTime(11, 0)))
        val view = dayView(listOf(milkDone, rentDone, dentistDone), history, friday, friday)
        assertEquals(listOf(dentistDone, milkDone), view.completed)
    }

    @Test
    fun `a past day lists only what was ticked off on it, read from the history`() {
        val thursday = friday.minusDays(1)
        val history = listOf(ticked(creatine, thursday.atTime(8, 5)), ticked(gym, thursday.atTime(18, 0)), ticked(milk, friday.atTime(9, 0)))
        val view = dayView(all, history, thursday, friday)
        assertEquals(listOf(gym, creatine), view.completed)
        assertEquals(DayView(completed = view.completed), view)
        assertEquals(emptyList<Task>(), dayView(all, history, friday.minusDays(2), friday).completed)
    }

    @Test
    fun `a task finished twice in a day is listed once, and not under Completed while still open`() {
        val twice = listOf(ticked(gym, friday.atTime(8, 0)), ticked(gym, friday.atTime(9, 0)))
        assertEquals(listOf(gym), dayView(listOf(gym), twice, friday, friday).completed)
        // Yesterday's creatine ticked late, today: today's is still open, so it stays under Today only.
        val view = dayView(listOf(creatine), listOf(ticked(creatine, friday.atTime(9, 0))), friday, friday)
        assertEquals(listOf(creatine), view.due)
        assertEquals(emptyList<Task>(), view.completed)
    }

    @Test
    fun `a future day lists one-off tasks due then and every repeat that falls on it`() {
        val monday = friday.plusDays(3)
        assertEquals(setOf(creatine, dentist, gym), dayView(all, emptyList(), monday, friday).due.toSet())
        val tuesday = friday.plusDays(4)
        assertEquals(listOf(creatine), dayView(all, emptyList(), tuesday, friday).due)
    }

    @Test
    fun `undated tasks show on every day from today until done, then only under the day they were done`() {
        val saturday = friday.plusDays(1)
        assertEquals(listOf(milk), dayView(all, emptyList(), saturday, friday).anytime)
        assertEquals(listOf(milk), dayView(all, emptyList(), friday.plusDays(60), friday).anytime)
        assertEquals(DayView(), dayView(listOf(milk), emptyList(), friday.minusDays(1), friday))

        val done = milk.copy(completedAt = instant(friday.atTime(9, 0)))
        val history = listOf(ticked(done, friday.atTime(9, 0)))
        assertEquals(listOf(done), dayView(listOf(done), history, friday, friday).completed)
        assertEquals(DayView(), dayView(listOf(done), history, saturday, friday))
    }

    @Test
    fun `repeats stop showing after the series ends`() {
        val short = task(6, "Antibiotics", Schedule(friday, listOf(morning), Repeat(end = RepeatEnd.After(3), anchor = friday)))
        assertEquals(listOf(short), dayView(listOf(short), emptyList(), friday.plusDays(2), friday).due)
        assertEquals(emptyList<Task>(), dayView(listOf(short), emptyList(), friday.plusDays(3), friday).due)
    }

    @Test
    fun `the strip marks today for overdue and undated tasks, and every day something falls on`() {
        val marked = markedDays(listOf(rent, milk, dentist), emptyList(), friday, friday, friday.plusDays(30))
        assertEquals(setOf(friday, friday.plusDays(3)), marked)
        val wednesday = friday.plusDays(5)
        assertTrue(wednesday in markedDays(listOf(gym), emptyList(), friday, friday, friday.plusDays(30)))
    }

    @Test
    fun `everyday repeats mark no days, and past days are marked by what was ticked off`() {
        val thursday = friday.minusDays(1)
        val everyDay = markedDays(listOf(creatine), listOf(ticked(creatine, thursday.atTime(8, 5))), friday, friday.minusDays(7), friday.plusDays(30))
        assertEquals(emptySet<LocalDate>(), everyDay)

        val history = listOf(ticked(gym, thursday.atTime(18, 0)), ticked(rent, friday.minusDays(3).atTime(9, 0)))
        assertEquals(setOf(thursday, friday.minusDays(3)), markedDays(listOf(gym, rent), history, friday, friday.minusDays(7), thursday))
    }
}
