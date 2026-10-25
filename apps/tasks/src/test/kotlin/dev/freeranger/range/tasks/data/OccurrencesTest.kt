package dev.freeranger.range.tasks.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class OccurrencesTest {
    // A Friday.
    private val friday = LocalDate.of(2026, 10, 23)
    private val morning = LocalTime.of(8, 0)
    private val evening = LocalTime.of(20, 0)
    private val at = Instant.EPOCH

    private fun task(schedule: Schedule?, now: LocalDateTime) =
        Task(title = "t", createdAt = Instant.EPOCH).withSchedule(schedule, now)

    private fun daily(vararg times: LocalTime, end: RepeatEnd = RepeatEnd.Never) =
        Schedule(friday, times.toList(), Repeat(end = end, anchor = friday))

    @Test
    fun `several times a day stay one task and count ticks`() {
        val first = task(daily(evening, morning), friday.atTime(7, 0))
        assertEquals(listOf(morning, evening), first.times)
        assertEquals(2, first.steps)

        val half = first.check(friday.atTime(12, 0), at)
        assertEquals(friday, half.dueDate)
        assertEquals(1, half.doneCount)

        val whole = half.check(friday.atTime(20, 5), at)
        assertEquals(friday.plusDays(1), whole.dueDate)
        assertEquals(0, whole.doneCount)
        assertEquals(friday, whole.lastDoneDay)
    }

    @Test
    fun `a repeating task can't be ticked off before its day`() {
        val done = task(daily(morning), friday.atTime(7, 0)).check(friday.atTime(8, 5), at)
        assertEquals(friday.plusDays(1), done.dueDate)
        assertFalse(done.canCheck(friday))
        assertTrue(done.canCheck(friday.plusDays(1)))
    }

    @Test
    fun `a one-off task can be ticked off early`() {
        assertTrue(task(Schedule(friday.plusDays(3)), friday.atTime(7, 0)).canCheck(friday))
    }

    @Test
    fun `done today shows under Completed until the day is over`() {
        val done = task(daily(morning), friday.atTime(7, 0)).check(friday.atTime(8, 5), friday.atTime(8, 5).toInstant(ZoneOffset.UTC))
        assertTrue(done.isDone(friday, ZoneOffset.UTC))
        assertFalse(done.isDone(friday.plusDays(1), ZoneOffset.UTC))
    }

    @Test
    fun `reopening a finished day takes back its last tick`() {
        val done = task(daily(morning, evening), friday.atTime(7, 0)).check(friday.atTime(8, 5), at).check(friday.atTime(20, 5), at)
        val reopened = done.reopen()
        assertEquals(friday, reopened.dueDate)
        assertEquals(1, reopened.doneCount)
        assertNull(reopened.lastDoneAt)
    }

    @Test
    fun `unticking takes back one tick and never goes below zero`() {
        val half = task(daily(morning, evening), friday.atTime(7, 0)).check(friday.atTime(8, 5), at)
        assertEquals(0, half.uncheck().doneCount)
        assertEquals(0, half.uncheck().uncheck().doneCount)
    }

    @Test
    fun `reminders fire at each time only while ticks are missing`() {
        val first = task(daily(morning, evening), friday.atTime(7, 0))
        assertEquals(friday.atTime(8, 0) to morning, first.nextReminder(friday.atTime(7, 0)))
        assertEquals(friday.atTime(20, 0) to evening, first.nextReminder(friday.atTime(9, 0)))

        // Ticking off early covers the morning reminder, but the evening one still comes.
        val early = first.check(friday.atTime(7, 30), at)
        assertEquals(friday.atTime(20, 0) to evening, early.nextReminder(friday.atTime(7, 30)))
        assertFalse(early.wantsReminder(morning))
    }

    @Test
    fun `an all-day task reminds once, at the time picked in Settings`() {
        val task = task(Schedule(friday), friday.atTime(7, 0))
        assertEquals(friday.atTime(9, 0) to null, task.nextReminder(friday.atTime(7, 0)))
        assertEquals(friday.atTime(7, 30) to null, task.nextReminder(friday.atTime(7, 0), allDayAt = LocalTime.of(7, 30)))
        assertEquals(null, task.nextReminder(friday.atTime(8, 0), allDayAt = LocalTime.of(7, 30)))
    }

    @Test
    fun `a task with several times is only overdue after its last time`() {
        val first = task(daily(morning, evening), friday.atTime(7, 0))
        assertFalse(first.isOverdue(friday.atTime(12, 0)))
        assertTrue(first.isOverdue(friday.atTime(20, 30)))
    }

    @Test
    fun `a new repeating series starts on the first day with something ahead`() {
        assertEquals(friday.plusDays(1), task(daily(morning), friday.atTime(21, 0)).dueDate)
        assertEquals(friday, task(daily(morning, evening), friday.atTime(12, 0)).dueDate)
    }

    @Test
    fun `a one-off task keeps its day even when the time has passed`() {
        val first = task(Schedule(friday, listOf(morning)), friday.atTime(21, 0))
        assertEquals(friday, first.dueDate)
        assertTrue(first.isOverdue(friday.atTime(21, 0)))
        assertNotNull(first.check(friday.atTime(21, 0), at).completedAt)
    }

    @Test
    fun `ticking off late skips days already in the past`() {
        val first = task(daily(), friday.atStartOfDay())
        assertEquals(friday.plusDays(3), first.check(friday.plusDays(3).atTime(12, 0), at).dueDate)
    }

    @Test
    fun `weekly on chosen days`() {
        val repeat = Repeat(unit = RepeatUnit.Week, weekdays = setOf(1, 3), anchor = friday) // Mon, Wed
        val first = task(Schedule(friday, emptyList(), repeat), friday.atStartOfDay())
        assertEquals(LocalDate.of(2026, 10, 26), first.dueDate)
        assertEquals(LocalDate.of(2026, 10, 28), first.check(LocalDate.of(2026, 10, 26).atTime(12, 0), at).dueDate)
    }

    @Test
    fun `every two weeks stays aligned to the start week`() {
        val repeat = Repeat(every = 2, unit = RepeatUnit.Week, anchor = friday)
        assertEquals(friday.plusWeeks(2), repeat.nextDate(friday))
        assertEquals(friday.plusWeeks(4), repeat.nextDate(friday.plusWeeks(2)))
    }

    @Test
    fun `monthly on the 31st falls back to the last day of shorter months`() {
        val repeat = Repeat(unit = RepeatUnit.Month, anchor = LocalDate.of(2026, 1, 31))
        assertEquals(LocalDate.of(2026, 2, 28), repeat.nextDate(LocalDate.of(2026, 1, 31)))
        assertEquals(LocalDate.of(2026, 3, 31), repeat.nextDate(LocalDate.of(2026, 2, 28)))
    }

    @Test
    fun `yearly on 29 February falls back to the 28th`() {
        val repeat = Repeat(unit = RepeatUnit.Year, anchor = LocalDate.of(2028, 2, 29))
        assertEquals(LocalDate.of(2029, 2, 28), repeat.nextDate(LocalDate.of(2028, 2, 29)))
    }

    @Test
    fun `ends after a number of days, counting a day with several times once`() {
        val first = task(daily(morning, evening, end = RepeatEnd.After(2)), friday.atTime(7, 0))
        val second = first.finishDay(friday.atTime(21, 0), at)
        assertEquals(2, second.occurrence)
        assertNull(second.completedAt)
        assertNotNull(second.finishDay(friday.plusDays(1).atTime(21, 0), at).completedAt)
    }

    @Test
    fun `ends on a date, inclusive`() {
        val first = task(daily(end = RepeatEnd.OnDate(friday.plusDays(1))), friday.atStartOfDay())
        val second = first.finishDay(friday.atTime(12, 0), at)
        assertEquals(friday.plusDays(1), second.dueDate)
        assertNotNull(second.finishDay(friday.plusDays(1).atTime(12, 0), at).completedAt)
    }

    @Test
    fun `removing the schedule clears every schedule field`() {
        val scheduled = task(daily(morning, evening), friday.atTime(7, 0)).check(friday.atTime(7, 30), at)
        assertEquals(Task(title = "t", createdAt = Instant.EPOCH), scheduled.withSchedule(null, friday.atTime(7, 0)))
    }
}
