package dev.freeranger.range.tasks.data

import android.content.Context
import androidx.room3.AutoMigration
import androidx.room3.ColumnTypeConverter
import androidx.room3.ColumnTypeConverters
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.DeleteColumn
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.Transaction
import androidx.room3.Update
import androidx.room3.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Dao
abstract class TaskDao {
    @Query("SELECT * FROM tasks")
    abstract fun observeAll(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    abstract fun observe(id: Long): Flow<Task?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    abstract suspend fun get(id: Long): Task?

    @Query("SELECT * FROM tasks WHERE completedAt IS NULL AND dueDate IS NOT NULL")
    abstract suspend fun openWithDueDate(): List<Task>

    @Insert
    abstract suspend fun insert(task: Task): Long

    @Update
    abstract suspend fun update(task: Task)

    /** Also removes the task's history, through the foreign key. */
    @Query("DELETE FROM tasks WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("SELECT * FROM completions")
    abstract fun observeCompletions(): Flow<List<Completion>>

    @Insert
    abstract suspend fun insertCompletion(completion: Completion): Long

    @Query("DELETE FROM completions WHERE taskId = :taskId AND at = :at")
    abstract suspend fun deleteCompletion(taskId: Long, at: Instant)

    /** Saves a task together with the change to its history, so the two never disagree. */
    @Transaction
    open suspend fun update(task: Task, finished: Completion?, undone: Instant?) {
        update(task)
        if (undone != null) deleteCompletion(task.id, undone)
        if (finished != null) insertCompletion(finished)
    }
}

@Database(
    entities = [Task::class, Completion::class],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = TasksDatabase.OneRowPerSeries::class),
        AutoMigration(from = 2, to = 3, spec = TasksDatabase.TimesAreReminders::class),
        AutoMigration(from = 3, to = 4, spec = TasksDatabase.CompletionHistory::class),
    ],
)
@ColumnTypeConverters(Converters::class)
abstract class TasksDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao

    /** v2: a repeating task stays one row and records which of its day's times are done, instead of a row per time. */
    @DeleteColumn(tableName = "tasks", columnName = "dueTime")
    class OneRowPerSeries : AutoMigrationSpec

    /**
     * v3: a task's times are reminders and progress is a count of ticks, not a list of ticked times.
     * Today's ticks on tasks with several times start over once.
     */
    @DeleteColumn(tableName = "tasks", columnName = "doneTimes")
    class TimesAreReminders : AutoMigrationSpec

    /**
     * v4: a history of finished days, so each day can list what was ticked off on it. Filled from
     * what tasks already remember: when one-off tasks were completed and each repeating task's
     * last finished day. Earlier days of repeating tasks were never stored.
     */
    class CompletionHistory : AutoMigrationSpec {
        override suspend fun onPostMigrate(connection: SQLiteConnection) {
            val finished = mutableListOf<Pair<Long, Long>>()
            connection.prepare("SELECT id, completedAt, lastDoneAt FROM tasks").use {
                while (it.step()) {
                    val id = it.getLong(0)
                    listOf(1, 2).filterNot(it::isNull).map(it::getLong).distinct().forEach { at -> finished += id to at }
                }
            }
            val zone = ZoneId.systemDefault()
            connection.prepare("INSERT INTO completions (taskId, day, at) VALUES (?, ?, ?)").use {
                finished.forEach { (id, at) ->
                    it.bindLong(1, id)
                    it.bindLong(2, Instant.ofEpochMilli(at).atZone(zone).toLocalDate().toEpochDay())
                    it.bindLong(3, at)
                    it.step()
                    it.reset()
                }
            }
        }
    }

    companion object {
        fun create(context: Context): TasksDatabase =
            Room.databaseBuilder(context, TasksDatabase::class.java, "tasks.db")
                .setDriver(AndroidSQLiteDriver())
                .build()
    }
}

internal object Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @ColumnTypeConverter fun fromDate(value: LocalDate?): Long? = value?.toEpochDay()
    @ColumnTypeConverter fun toDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @ColumnTypeConverter fun fromTime(value: LocalTime?): Int? = value?.toSecondOfDay()
    @ColumnTypeConverter fun toTime(value: Int?): LocalTime? = value?.let { LocalTime.ofSecondOfDay(it.toLong()) }

    @ColumnTypeConverter fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()
    @ColumnTypeConverter fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    /** Times of day as "08:00,20:00". */
    @ColumnTypeConverter fun fromTimes(value: List<LocalTime>): String = value.joinToString(",")
    @ColumnTypeConverter fun toTimes(value: String): List<LocalTime> =
        if (value.isEmpty()) emptyList() else value.split(",").map(LocalTime::parse)

    @ColumnTypeConverter fun fromRepeat(value: Repeat?): String? = value?.let { json.encodeToString(Repeat.serializer(), it) }
    @ColumnTypeConverter fun toRepeat(value: String?): Repeat? = value?.let { json.decodeFromString(Repeat.serializer(), it) }
}
