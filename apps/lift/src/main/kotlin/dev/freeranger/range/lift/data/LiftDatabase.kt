package dev.freeranger.range.lift.data

import android.content.Context
import androidx.room3.AutoMigration
import androidx.room3.ColumnTypeConverter
import androidx.room3.ColumnTypeConverters
import androidx.room3.Dao
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

    /** Keeps only what was ticked off, then marks the workout finished. */
    @Transaction
    open suspend fun finish(workout: Workout) {
        deleteUndoneSets(workout.id)
        deleteEmptyEntries(workout.id)
        update(workout)
    }
}

@Database(
    entities = [Exercise::class, Workout::class, WorkoutExercise::class, WorkoutSet::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = LiftDatabase.ExerciseTypes::class),
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
