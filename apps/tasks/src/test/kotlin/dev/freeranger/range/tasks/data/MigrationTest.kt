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
}
