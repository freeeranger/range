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
                listOf("", "Reps", "Bodyweight", "0", "0"),
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

    @Test
    fun `2 to 3 keeps workouts and adds notes`() = runTest {
        helper.createDatabase(2).apply {
            execSQL("INSERT INTO workouts (id, name, startedAt, finishedAt) VALUES (1, 'Legs', 0, 3600000)")
            close()
        }

        val connection = helper.runMigrationsAndValidate(3)
        connection.prepare("SELECT name, notes, templateId FROM workouts WHERE id = 1").use {
            it.step()
            assertEquals("Legs", it.getText(0))
            assertEquals("", it.getText(1))
            assertTrue(it.isNull(2))
        }
        connection.close()
    }

    @Test
    fun `3 to 4 swaps the rest timer for the stopwatch and keeps everything else`() = runTest {
        helper.createDatabase(3).apply {
            execSQL("INSERT INTO exercises (id, `key`, name, muscle, measure, load, perSide, archived, restSeconds) VALUES (1, 'squat', '', 'Legs', 'Reps', 'Weight', 0, 0, 180)")
            execSQL("INSERT INTO workouts (id, name, startedAt, finishedAt, notes, templateId, restEndsAt, restSeconds) VALUES (1, 'Legs', 0, NULL, 'Heavy', 2, 60000, 120)")
            close()
        }

        val connection = helper.runMigrationsAndValidate(4)
        connection.prepare("SELECT name, notes, templateId, lastSetAt FROM workouts WHERE id = 1").use {
            it.step()
            assertEquals("Legs", it.getText(0))
            assertEquals("Heavy", it.getText(1))
            assertEquals(2L, it.getLong(2))
            assertTrue(it.isNull(3))
        }
        connection.prepare("SELECT `key` FROM exercises WHERE id = 1").use {
            it.step()
            assertEquals("squat", it.getText(0))
        }
        connection.close()
    }

    @Test
    fun `4 to 5 retires built-in exercises, keeping those workouts or templates use`() = runTest {
        helper.createDatabase(4).apply {
            execSQL(
                "INSERT INTO exercises (id, `key`, name, muscle, measure, load, perSide, archived) VALUES " +
                    "(1, 'squat', '', 'Legs', 'Reps', 'Weight', 0, 0), (2, 'bench_press', '', 'Chest', 'Reps', 'Weight', 0, 0), " +
                    "(3, 'plank', '', 'Core', 'Time', 'Bodyweight', 0, 0), (4, NULL, 'Landmine press', 'Shoulders', 'Reps', 'Weight', 1, 0)",
            )
            execSQL("INSERT INTO workouts (id, name, startedAt, finishedAt, notes) VALUES (1, 'Legs', 0, 3600000, '')")
            execSQL("INSERT INTO workout_exercises (id, workoutId, exerciseId, position) VALUES (1, 1, 1, 0)")
            execSQL("INSERT INTO templates (id, name, createdAt) VALUES (1, 'Push', 0)")
            execSQL("INSERT INTO template_exercises (id, templateId, exerciseId, position, sets) VALUES (1, 1, 2, 0, 3)")
            close()
        }

        val connection = helper.runMigrationsAndValidate(5)
        val exercises = mutableListOf<Pair<Long, Long>>()
        connection.prepare("SELECT id, archived FROM exercises ORDER BY id").use {
            while (it.step()) exercises += it.getLong(0) to it.getLong(1)
        }
        assertEquals(listOf(1L to 1L, 2L to 1L, 4L to 0L), exercises)
        connection.close()
    }

    @Test
    fun `5 to 6 makes extra weight and help one signed bodyweight load`() = runTest {
        helper.createDatabase(5).apply {
            execSQL(
                "INSERT INTO exercises (id, `key`, name, muscle, measure, load, perSide, archived) VALUES " +
                    "(1, NULL, 'Dip', 'Chest', 'Reps', 'ExtraWeight', 0, 0), (2, NULL, 'Band pull-up', 'Back', 'Reps', 'Assisted', 0, 0), " +
                    "(3, NULL, 'Squat', 'Legs', 'Reps', 'Weight', 0, 0)",
            )
            execSQL("INSERT INTO workouts (id, name, startedAt, finishedAt, notes) VALUES (1, '', 0, 3600000, '')")
            execSQL("INSERT INTO workout_exercises (id, workoutId, exerciseId, position) VALUES (1, 1, 1, 0), (2, 1, 2, 1), (3, 1, 3, 2)")
            execSQL("INSERT INTO sets (id, workoutExerciseId, position, weight, reps, done) VALUES (1, 1, 0, 10.0, 8, 1), (2, 2, 0, 20.0, 6, 1), (3, 2, 1, NULL, 3, 1), (4, 3, 0, 100.0, 5, 1)")
            close()
        }

        val connection = helper.runMigrationsAndValidate(6)
        val loads = mutableListOf<String>()
        connection.prepare("SELECT load FROM exercises ORDER BY id").use { while (it.step()) loads += it.getText(0) }
        assertEquals(listOf("Bodyweight", "Bodyweight", "Weight"), loads)
        val weights = mutableListOf<Double?>()
        connection.prepare("SELECT weight FROM sets ORDER BY id").use { while (it.step()) weights += if (it.isNull(0)) null else it.getDouble(0) }
        assertEquals(listOf(10.0, -20.0, null, 100.0), weights)
        connection.close()
    }
}
