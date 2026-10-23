package dev.freeranger.range.lift.data

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

/** Upgrades databases written by older versions, checked against the schemas committed in apps/lift/schemas. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        File.createTempFile("migration", ".db"),
        AndroidSQLiteDriver(),
        LiftDatabase::class,
    )

    @Test
    fun `1 to 2 keeps exercises and sets, and gives built-in exercises their types`() = runTest {
        helper.createDatabase(1).apply {
            execSQL("INSERT INTO exercises (id, `key`, name, muscle) VALUES (1, 'pull_up', '', 'Back'), (2, 'bench_press', '', 'Chest'), (3, NULL, 'Landmine press', 'Shoulders')")
            execSQL("INSERT INTO workouts (id, name, startedAt, finishedAt) VALUES (1, '', 0, 3600000)")
            execSQL("INSERT INTO workout_exercises (id, workoutId, exerciseId, position) VALUES (1, 1, 1, 0)")
            execSQL("INSERT INTO sets (id, workoutExerciseId, position, weight, reps, done) VALUES (1, 1, 0, 10.0, 8, 1)")
            close()
        }

        val connection = helper.runMigrationsAndValidate(2)
        val exercises = mutableListOf<List<String>>()
        connection.prepare("SELECT name, measure, load, perSide, archived FROM exercises ORDER BY id").use {
            while (it.step()) exercises += (0..4).map(it::getText)
        }
        assertEquals(
            listOf(
                listOf("", "Reps", "ExtraWeight", "0", "0"),
                listOf("", "Reps", "Weight", "0", "0"),
                listOf("Landmine press", "Reps", "Weight", "0", "0"),
            ),
            exercises,
        )
        connection.prepare("SELECT weight, reps, done, seconds FROM sets WHERE id = 1").use {
            it.step()
            assertEquals(10.0, it.getDouble(0), 0.001)
            assertEquals(8L, it.getLong(1))
            assertEquals(1L, it.getLong(2))
            assertTrue(it.isNull(3))
        }
        connection.close()
    }
}
