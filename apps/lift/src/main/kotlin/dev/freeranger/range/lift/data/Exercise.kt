package dev.freeranger.range.lift.data

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** What an exercise mainly trains: how the exercise list is grouped. */
enum class Muscle { Chest, Back, Legs, Shoulders, Arms, Core }

/**
 * An exercise in the library. Built-in exercises have a [key] and are named in the app's language;
 * the user's own have a [name] instead.
 */
@Entity(tableName = "exercises", indices = [Index("key", unique = true)])
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable id of a built-in exercise, e.g. "bench_press". Null for exercises the user added. */
    val key: String? = null,
    /** The user's name for their own exercise. Empty for built-in ones. */
    val name: String = "",
    val muscle: Muscle,
)

/** The exercises every library starts with. Adding one here adds it for existing users on the next launch. */
object BuiltInExercises {
    val all: List<Pair<String, Muscle>> = listOf(
        "bench_press" to Muscle.Chest,
        "incline_bench_press" to Muscle.Chest,
        "dumbbell_bench_press" to Muscle.Chest,
        "push_up" to Muscle.Chest,
        "cable_fly" to Muscle.Chest,
        "dip" to Muscle.Chest,
        "deadlift" to Muscle.Back,
        "barbell_row" to Muscle.Back,
        "pull_up" to Muscle.Back,
        "lat_pulldown" to Muscle.Back,
        "seated_cable_row" to Muscle.Back,
        "squat" to Muscle.Legs,
        "front_squat" to Muscle.Legs,
        "romanian_deadlift" to Muscle.Legs,
        "leg_press" to Muscle.Legs,
        "leg_curl" to Muscle.Legs,
        "leg_extension" to Muscle.Legs,
        "bulgarian_split_squat" to Muscle.Legs,
        "hip_thrust" to Muscle.Legs,
        "calf_raise" to Muscle.Legs,
        "overhead_press" to Muscle.Shoulders,
        "dumbbell_shoulder_press" to Muscle.Shoulders,
        "lateral_raise" to Muscle.Shoulders,
        "face_pull" to Muscle.Shoulders,
        "barbell_curl" to Muscle.Arms,
        "dumbbell_curl" to Muscle.Arms,
        "hammer_curl" to Muscle.Arms,
        "triceps_pushdown" to Muscle.Arms,
        "skull_crusher" to Muscle.Arms,
        "hanging_leg_raise" to Muscle.Core,
        "cable_crunch" to Muscle.Core,
        "ab_wheel" to Muscle.Core,
    )
}
