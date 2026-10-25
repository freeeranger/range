package dev.freeranger.range.notes.data

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Upgrades databases written by older versions, checked against the schemas committed in apps/notes/schemas. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        File.createTempFile("migration", ".db"),
        AndroidSQLiteDriver(),
        NotesDatabase::class,
    )

    @Test
    fun `1 to 2 keeps every note, none of them locked`() = runTest {
        helper.createDatabase(1).apply {
            execSQL("INSERT INTO notes (id, title, body, items, pinned, createdAt, editedAt) VALUES (1, 'Ideas', '# Big', NULL, 1, 0, 0), (2, 'Shop', '', '[{\"id\":1,\"text\":\"Milk\"}]', 0, 0, 0)")
            close()
        }
        val connection = helper.runMigrationsAndValidate(2)
        val rows = mutableListOf<List<String?>>()
        connection.prepare("SELECT title, body, locked, secret FROM notes ORDER BY id").use {
            while (it.step()) rows += listOf(it.getText(0), it.getText(1), it.getText(2), if (it.isNull(3)) null else it.getText(3))
        }
        assertEquals(listOf(listOf("Ideas", "# Big", "0", null), listOf("Shop", "", "0", null)), rows)
        connection.prepare("SELECT items FROM notes WHERE id = 2").use { assertTrue(it.step() && it.getText(0).contains("Milk")) }
        connection.close()
    }
}
