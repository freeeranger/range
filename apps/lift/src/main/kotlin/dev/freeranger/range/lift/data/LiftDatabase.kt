package dev.freeranger.range.lift.data

import android.content.Context
import androidx.room3.AutoMigration
import androidx.room3.ColumnTypeConverter
import androidx.room3.ColumnTypeConverters
import androidx.room3.Dao
import androidx.room3.DeleteColumn
import androidx.room3.Database
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.Transaction
import androidx.room3.Update
import androidx.room3.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
abstract class LiftDao {
    @Query("SELECT * FROM exercises")
    abstract fun observeExercises(): Flow<List<Exercise>>

    @Query("SELECT * FROM workouts")
    abstract fun observeWorkouts(): Flow<List<Workout>>

    @Query("SELECT * FROM workout_exercises")
    abstract fun observeEntries(): Flow<List<WorkoutExercise>>

    @Query("SELECT * FROM sets")
    abstract fun observeSets(): Flow<List<WorkoutSet>>

    @Query("SELECT * FROM workouts WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    abstract fun observeInProgress(): Flow<Workout?>

    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId")
    abstract fun observeEntriesOf(workoutId: Long): Flow<List<WorkoutExercise>>

    @Query("SELECT s.* FROM sets s JOIN workout_exercises e ON s.workoutExerciseId = e.id WHERE e.workoutId = :workoutId")
    abstract fun observeSetsOf(workoutId: Long): Flow<List<WorkoutSet>>

    @Query("SELECT * FROM templates")
    abstract fun observeTemplates(): Flow<List<Template>>

    @Query("SELECT * FROM template_exercises")
    abstract fun observeTemplateExercises(): Flow<List<TemplateExercise>>

    @Query("SELECT * FROM templates WHERE id = :id")
    abstract suspend fun template(id: Long): Template?

    @Query("SELECT * FROM template_exercises WHERE templateId = :templateId ORDER BY position")
    abstract suspend fun templateExercises(templateId: Long): List<TemplateExercise>

    @Insert
    abstract suspend fun insert(template: Template): Long

    @Update
    abstract suspend fun update(template: Template)

    @Insert
    abstract suspend fun insertTemplateExercises(entries: List<TemplateExercise>)

    @Query("DELETE FROM template_exercises WHERE templateId = :templateId")
    abstract suspend fun clearTemplate(templateId: Long)

    @Query("SELECT * FROM template_exercises WHERE id = :id")
    abstract suspend fun templateExercise(id: Long): TemplateExercise?

    @Query("SELECT MAX(position) FROM template_exercises WHERE templateId = :templateId")
    abstract suspend fun lastTemplatePosition(templateId: Long): Int?

    @Query("UPDATE template_exercises SET sets = :sets WHERE id = :id")
    abstract suspend fun setTemplateSets(id: Long, sets: Int)

    @Query("UPDATE template_exercises SET exerciseId = :exerciseId WHERE id = :id")
    abstract suspend fun setTemplateExercise(id: Long, exerciseId: Long)

    @Query("UPDATE template_exercises SET position = :position WHERE id = :id")
    abstract suspend fun setTemplatePosition(id: Long, position: Int)

    @Query("DELETE FROM template_exercises WHERE id = :id")
    abstract suspend fun deleteTemplateExercise(id: Long)

    /** Puts a template's exercises in the order given. */
    @Transaction
    open suspend fun reorderTemplate(entryIds: List<Long>) {
        entryIds.forEachIndexed { position, id -> setTemplatePosition(id, position) }
    }

    /** Also removes its exercises, through the foreign key. */
    @Query("DELETE FROM templates WHERE id = :id")
    abstract suspend fun deleteTemplate(id: Long)

    /** Saves a new template with its exercises, returning its id. */
    @Transaction
    open suspend fun insert(template: Template, entries: List<TemplateExercise>): Long {
        val id = insert(template)
        insertTemplateExercises(entries.map { it.copy(templateId = id) })
        return id
    }

    /** Replaces a template's exercises. */
    @Transaction
    open suspend fun replace(templateId: Long, entries: List<TemplateExercise>) {
        clearTemplate(templateId)
        insertTemplateExercises(entries.map { it.copy(templateId = templateId) })
    }

    /** Starts a workout with its exercises and their first sets in one go. */
    @Transaction
    open suspend fun insert(workout: Workout, entries: List<Pair<WorkoutExercise, List<WorkoutSet>>>): Long {
        val id = insert(workout)
        entries.forEach { (entry, sets) -> insert(entry.copy(workoutId = id), sets) }
        return id
    }

    @Insert
    abstract suspend fun insert(exercise: Exercise): Long

    @Update
    abstract suspend fun update(exercise: Exercise)

    @Query("SELECT * FROM exercises WHERE id = :id")
    abstract suspend fun exercise(id: Long): Exercise?

    /** Built-in exercises already there are kept as they are. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertBuiltIn(exercises: List<Exercise>)

    @Query("SELECT * FROM workouts WHERE id = :id")
    abstract suspend fun workout(id: Long): Workout?

    @Query("SELECT * FROM workouts WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    abstract suspend fun inProgress(): Workout?

    @Insert
    abstract suspend fun insert(workout: Workout): Long

    @Update
    abstract suspend fun update(workout: Workout)

    /** Also removes its exercises and sets, through the foreign keys. */
    @Query("DELETE FROM workouts WHERE id = :id")
    abstract suspend fun deleteWorkout(id: Long)

    @Query("SELECT MAX(position) FROM workout_exercises WHERE workoutId = :workoutId")
    abstract suspend fun lastPosition(workoutId: Long): Int?

    @Insert
    abstract suspend fun insert(entry: WorkoutExercise): Long

    @Query("DELETE FROM workout_exercises WHERE id = :id")
    abstract suspend fun deleteEntry(id: Long)

    @Query("UPDATE workout_exercises SET exerciseId = :exerciseId WHERE id = :id")
    abstract suspend fun setEntryExercise(id: Long, exerciseId: Long)

    @Query("DELETE FROM sets WHERE workoutExerciseId = :entryId")
    abstract suspend fun deleteSetsOf(entryId: Long)

    /** Puts another exercise in a workout's place [entryId], with [sets] empty sets for it. */
    @Transaction
    open suspend fun swap(entryId: Long, exerciseId: Long, sets: Int) {
        setEntryExercise(entryId, exerciseId)
        deleteSetsOf(entryId)
        insertSets(List(sets) { WorkoutSet(workoutExerciseId = entryId, position = it) })
    }

    @Query("UPDATE workout_exercises SET position = :position WHERE id = :id")
    abstract suspend fun setPosition(id: Long, position: Int)

    /** Puts a workout's exercises in the order given. */
    @Transaction
    open suspend fun reorder(entryIds: List<Long>) {
        entryIds.forEachIndexed { position, id -> setPosition(id, position) }
    }

    @Query("SELECT * FROM sets WHERE workoutExerciseId = :entryId ORDER BY position")
    abstract suspend fun sets(entryId: Long): List<WorkoutSet>

    @Insert
    abstract suspend fun insert(set: WorkoutSet): Long

    @Insert
    abstract suspend fun insertSets(sets: List<WorkoutSet>)

    @Update
    abstract suspend fun update(set: WorkoutSet)

    @Query("DELETE FROM sets WHERE id = :id")
    abstract suspend fun deleteSet(id: Long)

    /** The finished sets from the latest finished workout that included the exercise. */
    @Query(
        """
        SELECT s.* FROM sets s JOIN workout_exercises e ON s.workoutExerciseId = e.id
        WHERE s.done = 1 AND e.exerciseId = :exerciseId AND e.workoutId = (
            SELECT w.id FROM workouts w JOIN workout_exercises we ON we.workoutId = w.id
            WHERE we.exerciseId = :exerciseId AND w.finishedAt IS NOT NULL
            ORDER BY w.startedAt DESC LIMIT 1
        )
        ORDER BY e.position, s.position
        """,
    )
    abstract suspend fun lastSets(exerciseId: Long): List<WorkoutSet>

    @Query("DELETE FROM sets WHERE done = 0 AND workoutExerciseId IN (SELECT id FROM workout_exercises WHERE workoutId = :workoutId)")
    abstract suspend fun deleteUndoneSets(workoutId: Long)

    @Query("DELETE FROM workout_exercises WHERE workoutId = :workoutId AND id NOT IN (SELECT workoutExerciseId FROM sets)")
    abstract suspend fun deleteEmptyEntries(workoutId: Long)

    /** Adds an exercise to a workout together with its first sets. */
    @Transaction
    open suspend fun insert(entry: WorkoutExercise, sets: List<WorkoutSet>): Long {
        val id = insert(entry)
        insertSets(sets.map { it.copy(workoutExerciseId = id) })
        return id
    }

    /** Drops sets never ticked off, and exercises left with none. */
    @Transaction
    open suspend fun tidy(workoutId: Long) {
        deleteUndoneSets(workoutId)
        deleteEmptyEntries(workoutId)
    }

    @Query("SELECT * FROM exercises")
    abstract suspend fun allExercises(): List<Exercise>

    @Query("SELECT * FROM workouts")
    abstract suspend fun allWorkouts(): List<Workout>

    @Query("SELECT * FROM workout_exercises")
    abstract suspend fun allEntries(): List<WorkoutExercise>

    @Query("SELECT * FROM sets")
    abstract suspend fun allSets(): List<WorkoutSet>

    @Query("SELECT * FROM templates")
    abstract suspend fun allTemplates(): List<Template>

    @Query("SELECT * FROM template_exercises")
    abstract suspend fun allTemplateExercises(): List<TemplateExercise>

    @Query("DELETE FROM template_exercises")
    abstract suspend fun clearTemplateExercises()

    @Query("DELETE FROM templates")
    abstract suspend fun clearTemplates()

    @Query("DELETE FROM sets")
    abstract suspend fun clearSets()

    @Query("DELETE FROM workout_exercises")
    abstract suspend fun clearEntries()

    @Query("DELETE FROM workouts")
    abstract suspend fun clearWorkouts()

    @Query("DELETE FROM exercises")
    abstract suspend fun clearExercises()

    @Insert
    abstract suspend fun insertExercises(exercises: List<Exercise>)

    @Insert
    abstract suspend fun insertWorkouts(workouts: List<Workout>)

    @Insert
    abstract suspend fun insertEntries(entries: List<WorkoutExercise>)

    @Insert
    abstract suspend fun insertTemplates(templates: List<Template>)

    /** Replaces everything with [backup], all or nothing. */
    @Transaction
    open suspend fun replaceAll(backup: Backup) {
        clearTemplateExercises()
        clearTemplates()
        clearSets()
        clearEntries()
        clearWorkouts()
        clearExercises()
        insertExercises(backup.exercises)
        insertWorkouts(backup.workouts)
        insertEntries(backup.entries)
        insertSets(backup.sets)
        insertTemplates(backup.templates)
        insertTemplateExercises(backup.templateExercises)
    }

    /** Keeps only what was ticked off, then marks the workout finished. */
    @Transaction
    open suspend fun finish(workout: Workout) {
        deleteUndoneSets(workout.id)
        deleteEmptyEntries(workout.id)
        update(workout)
    }
}

@Database(
    entities = [Exercise::class, Workout::class, WorkoutExercise::class, WorkoutSet::class, Template::class, TemplateExercise::class],
    version = 6,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = LiftDatabase.ExerciseTypes::class),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4, spec = LiftDatabase.RestStopwatch::class),
        AutoMigration(from = 4, to = 5, spec = LiftDatabase.RetireBuiltIns::class),
        AutoMigration(from = 5, to = 6, spec = LiftDatabase.SignedBodyweight::class),
    ],
)
@ColumnTypeConverters(Converters::class)
abstract class LiftDatabase : RoomDatabase() {
    abstract fun lift(): LiftDao

    /**
     * v2: exercises say how they're measured (reps or time), what their weight means, whether
     * they're done a side at a time, and whether they were removed; sets can be timed. Built-in
     * exercises already in the library get their types; new ones arrive on the next launch.
     */
    class ExerciseTypes : AutoMigrationSpec {
        override suspend fun onPostMigrate(connection: SQLiteConnection) {
            connection.prepare("UPDATE exercises SET measure = ?, load = ?, perSide = ? WHERE `key` = ?").use {
                BuiltInExercises.all.forEach { builtIn ->
                    it.bindText(1, builtIn.measure.name)
                    it.bindText(2, builtIn.load.name)
                    it.bindLong(3, if (builtIn.perSide) 1 else 0)
                    it.bindText(4, builtIn.key)
                    it.step()
                    it.reset()
                }
            }
        }
    }

    // v3: a rest timer per exercise and on each workout, notes, and workout templates.

    /** v4: rest counts up from the last set instead of down to a set time, so the timer's columns go. */
    @DeleteColumn.Entries(
        DeleteColumn(tableName = "exercises", columnName = "restSeconds"),
        DeleteColumn(tableName = "workouts", columnName = "restEndsAt"),
        DeleteColumn(tableName = "workouts", columnName = "restSeconds"),
    )
    class RestStopwatch : AutoMigrationSpec

    /**
     * v5: libraries start with only the [BuiltInExercises.offered] built-ins. The others go, unless a
     * workout or template uses them; those are kept but removed from the library.
     */
    class RetireBuiltIns : AutoMigrationSpec {
        override suspend fun onPostMigrate(connection: SQLiteConnection) {
            val offered = BuiltInExercises.offered.map { it.key }
            val retired = "`key` IS NOT NULL AND `key` NOT IN (${offered.joinToString { "?" }})"
            fun run(sql: String) = connection.prepare(sql).use { statement ->
                offered.forEachIndexed { i, key -> statement.bindText(i + 1, key) }
                statement.step()
            }
            run(
                "DELETE FROM exercises WHERE $retired AND id NOT IN (SELECT exerciseId FROM workout_exercises) " +
                    "AND id NOT IN (SELECT exerciseId FROM template_exercises)",
            )
            run("UPDATE exercises SET archived = 1 WHERE $retired")
        }
    }

    /**
     * v6: "bodyweight + extra" and "assisted" exercises become plain bodyweight ones, whose weight
     * is signed: extra stays positive, and help, which was logged positive, turns negative.
     */
    class SignedBodyweight : AutoMigrationSpec {
        override suspend fun onPostMigrate(connection: SQLiteConnection) {
            connection.execSQL(
                "UPDATE sets SET weight = -weight WHERE weight IS NOT NULL AND workoutExerciseId IN " +
                    "(SELECT e.id FROM workout_exercises e JOIN exercises x ON e.exerciseId = x.id WHERE x.load = 'Assisted')",
            )
            connection.execSQL("UPDATE exercises SET load = 'Bodyweight' WHERE load IN ('ExtraWeight', 'Assisted')")
        }
    }

    companion object {
        fun create(context: Context): LiftDatabase =
            Room.databaseBuilder(context, LiftDatabase::class.java, "lift.db")
                .setDriver(AndroidSQLiteDriver())
                .build()
    }
}

internal object Converters {
    @ColumnTypeConverter fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()
    @ColumnTypeConverter fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    /** By name, so reordering the enum never changes what's stored. */
    @ColumnTypeConverter fun fromMuscle(value: Muscle): String = value.name
    @ColumnTypeConverter fun toMuscle(value: String): Muscle = Muscle.valueOf(value)
    @ColumnTypeConverter fun fromMeasure(value: Measure): String = value.name
    @ColumnTypeConverter fun toMeasure(value: String): Measure = Measure.valueOf(value)
    @ColumnTypeConverter fun fromLoad(value: Load): String = value.name
    @ColumnTypeConverter fun toLoad(value: String): Load = Load.valueOf(value)
}
