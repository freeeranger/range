package dev.freeranger.range.tasks.ui.list

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
        val view = dayView(all, friday, friday)
        assertEquals(listOf(rent), view.overdue)
        assertEquals(listOf(creatine), view.due)
        assertEquals(listOf(milk), view.anytime)
    }

    @Test
    fun `tomorrow's repeat of a task done today stays off today's list`() {
        val done = creatine.check(friday.atTime(8, 5), friday.atTime(8, 5).atZone(ZoneId.systemDefault()).toInstant())
        val view = dayView(listOf(done), friday, friday)
        assertEquals(emptyList<Task>(), view.due)
        assertEquals(listOf(done), view.completed)
        assertEquals(listOf(done), dayView(listOf(done), friday.plusDays(1), friday).due)
    }

    @Test
    fun `a future day lists one-off tasks due then and every repeat that falls on it`() {
        val monday = friday.plusDays(3)
        assertEquals(setOf(creatine, dentist, gym), dayView(all, monday, friday).due.toSet())
        val tuesday = friday.plusDays(4)
        assertEquals(listOf(creatine), dayView(all, tuesday, friday).due)
    }

    @Test
    fun `repeats stop showing after the series ends`() {
        val short = task(6, "Antibiotics", Schedule(friday, listOf(morning), Repeat(end = RepeatEnd.After(3), anchor = friday)))
        assertEquals(listOf(short), dayView(listOf(short), friday.plusDays(2), friday).due)
        assertEquals(emptyList<Task>(), dayView(listOf(short), friday.plusDays(3), friday).due)
    }

    @Test
    fun `the strip marks today for overdue and undated tasks, and every day something falls on`() {
        val marked = markedDays(listOf(rent, milk, dentist), friday, friday.plusDays(30))
        assertEquals(setOf(friday, friday.plusDays(3)), marked)
        assertTrue(friday.plusDays(10) in markedDays(listOf(creatine), friday, friday.plusDays(30)))
    }
}
