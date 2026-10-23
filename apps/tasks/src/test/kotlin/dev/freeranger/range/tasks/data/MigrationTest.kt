package dev.freeranger.range.tasks.data

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Upgrades databases written by older versions, checked against the schemas committed in apps/tasks/schemas. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        File.createTempFile("migration", ".db"),
        AndroidSQLiteDriver(),
        TasksDatabase::class,
    )

    @Test
    fun `1 to 2 keeps tasks and starts with no times ticked off`() = runTest {
        helper.createDatabase(1).apply {
            execSQL(
                "INSERT INTO tasks (id, title, details, dueDate, dueTime, times, repeat, occurrence, completedAt, createdAt) " +
                    "VALUES (1, 'Take vitamins', '', 20384, 28800, '08:00,20:00', NULL, 1, NULL, 0)",
            )
            close()
        }

        val connection = helper.runMigrationsAndValidate(2)
        connection.prepare("SELECT title, times, doneTimes FROM tasks WHERE id = 1").use {
            it.step()
            assertEquals("Take vitamins", it.getText(0))
            assertEquals("08:00,20:00", it.getText(1))
            assertEquals("", it.getText(2))
        }
        connection.close()
    }

    @Test
    fun `2 to 3 keeps tasks and starts the tick count at zero`() = runTest {
        helper.createDatabase(2).apply {
            execSQL(
                "INSERT INTO tasks (id, title, details, dueDate, times, doneTimes, repeat, occurrence, completedAt, lastDoneAt, lastDoneDay, createdAt) " +
                    "VALUES (1, 'Take vitamins', '', 20384, '08:00,20:00', '08:00', NULL, 1, NULL, NULL, NULL, 0)",
            )
            close()
        }

        val connection = helper.runMigrationsAndValidate(3)
        connection.prepare("SELECT title, times, doneCount FROM tasks WHERE id = 1").use {
            it.step()
            assertEquals("Take vitamins", it.getText(0))
            assertEquals("08:00,20:00", it.getText(1))
            assertEquals(0L, it.getLong(2))
        }
        connection.close()
    }

    @Test
    fun `3 to 4 fills the history from completed tasks and each repeat's last finished day`() = runTest {
        val friday = LocalDate.of(2026, 10, 23)
        fun millis(day: LocalDate, hour: Int) = day.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val rent = millis(friday.minusDays(3), 9)
        val vitamins = millis(friday, 20)
        val last = millis(friday.minusDays(1), 18)
        helper.createDatabase(3).apply {
            execSQL(
                "INSERT INTO tasks (id, title, details, dueDate, times, doneCount, repeat, occurrence, completedAt, lastDoneAt, lastDoneDay, createdAt) VALUES " +
                    "(1, 'Pay rent', '', ${friday.minusDays(3).toEpochDay()}, '', 1, NULL, 1, $rent, NULL, NULL, 0), " +
                    "(2, 'Take vitamins', '', ${friday.plusDays(1).toEpochDay()}, '08:00,20:00', 0, '{}', 2, NULL, $vitamins, ${friday.toEpochDay()}, 0), " +
                    "(3, 'Antibiotics', '', ${friday.minusDays(1).toEpochDay()}, '', 1, '{}', 3, $last, $last, ${friday.minusDays(1).toEpochDay()}, 0), " +
                    "(4, 'Buy oat milk', '', NULL, '', 0, NULL, 1, NULL, NULL, NULL, 0)",
            )
            close()
        }

        val connection = helper.runMigrationsAndValidate(4)
        val rows = buildList {
            connection.prepare("SELECT taskId, day, at FROM completions ORDER BY taskId").use {
                while (it.step()) add(Triple(it.getLong(0), LocalDate.ofEpochDay(it.getLong(1)), it.getLong(2)))
            }
        }
        assertEquals(
            listOf(Triple(1L, friday.minusDays(3), rent), Triple(2L, friday, vitamins), Triple(3L, friday.minusDays(1), last)),
            rows,
        )
        connection.close()
    }
}
