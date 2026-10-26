package dev.freeranger.range.notes.data

import android.content.Context
import androidx.room3.AutoMigration
import androidx.room3.ColumnTypeConverter
import androidx.room3.ColumnTypeConverters
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.Transaction
import androidx.room3.Update
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant

@Dao
abstract class NoteDao {
    /** Pinned first, then the most recently edited. */
    @Query("SELECT * FROM notes ORDER BY pinned DESC, editedAt DESC, id DESC")
    abstract fun observeAll(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    abstract fun observe(id: Long): Flow<Note?>

    @Query("SELECT * FROM notes WHERE id = :id")
    abstract suspend fun get(id: Long): Note?

    @Query("SELECT * FROM notes")
    abstract suspend fun all(): List<Note>

    @Insert
    abstract suspend fun insert(note: Note): Long

    @Insert
    abstract suspend fun insertAll(notes: List<Note>)

    @Update
    abstract suspend fun update(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM notes")
    abstract suspend fun clear()

    @Query("SELECT EXISTS(SELECT 1 FROM notes WHERE locked)")
    abstract suspend fun anyLocked(): Boolean

    /** Replaces everything with [notes], all or nothing. */
    @Transaction
    open suspend fun replaceAll(notes: List<Note>) {
        clear()
        insertAll(notes)
    }
}

/** v2: notes can be locked, their content encrypted. v3: notes can have pictures. */
@Database(
    entities = [Note::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
@ColumnTypeConverters(Converters::class)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun notes(): NoteDao

    companion object {
        fun create(context: Context): NotesDatabase =
            Room.databaseBuilder(context, NotesDatabase::class.java, "notes.db")
                .setDriver(AndroidSQLiteDriver())
                .build()
    }
}

internal object Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @ColumnTypeConverter fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()
    @ColumnTypeConverter fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    /** A checklist's items as JSON, so an item can gain fields without a schema migration. */
    @ColumnTypeConverter fun fromItems(value: List<ChecklistItem>?): String? = value?.let { json.encodeToString(ListSerializer(ChecklistItem.serializer()), it) }
    @ColumnTypeConverter fun toItems(value: String?): List<ChecklistItem>? = value?.let { json.decodeFromString(ListSerializer(ChecklistItem.serializer()), it) }

    @ColumnTypeConverter fun fromNames(value: List<String>): String = json.encodeToString(ListSerializer(String.serializer()), value)
    @ColumnTypeConverter fun toNames(value: String): List<String> = json.decodeFromString(ListSerializer(String.serializer()), value)
}
