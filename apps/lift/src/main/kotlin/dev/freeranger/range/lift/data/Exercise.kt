package dev.freeranger.range.lift.data

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** What an exercise mainly trains: how the exercise list is grouped. */
enum class Muscle { Chest, Back, Legs, Shoulders, Arms, Core }

/** What a set of the exercise counts: repetitions, or how long it was held or kept up. */
enum class Measure { Reps, Time }

/** What the weight logged with a set means, if the exercise takes one at all. */
enum class Load {
    /** A barbell, dumbbells or a machine: the weight lifted. */
    Weight,

    /** The body alone, with no weight to log. */
    Bodyweight,

    /** The body plus optional extra weight, like a weighted pull-up: the extra is logged. */
    ExtraWeight,

    /** The body with help from a band or machine: the weight taken off is logged, and less is better. */
    Assisted,
}

/**
 * An exercise in the library. Built-in exercises have a [key] and are named in the app's language
 * unless the user renamed them; the user's own have a [name].
 */
@Entity(tableName = "exercises", indices = [Index("key", unique = true)])
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable id of a built-in exercise, e.g. "bench_press". Null for exercises the user added. */
    val key: String? = null,
    /** The user's name for the exercise. Empty for a built-in one that keeps its translated name. */
    val name: String = "",
    val muscle: Muscle,
    @ColumnInfo(defaultValue = "Reps") val measure: Measure = Measure.Reps,
    @ColumnInfo(defaultValue = "Weight") val load: Load = Load.Weight,
    /** Sets are logged for one side at a time, like lunges or one-arm rows. */
    @ColumnInfo(defaultValue = "0") val perSide: Boolean = false,
    /**
     * Removed from the library: no longer offered for new workouts, but kept so past workouts still
     * show it. A row rather than a deletion also stops a removed built-in from coming back on launch.
     */
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
) {
    /** Sets carry a weight: shown as a column while logging. */
    val weighted: Boolean get() = load != Load.Bodyweight

    /** Comparing sets by estimated one-rep max makes sense: a weight lifted for reps. */
    val hasOneRepMax: Boolean get() = load == Load.Weight && measure == Measure.Reps

    /** Orders sets from worst to best: heavier, longer or more reps, or for assisted ones, less help. */
    val setOrder: Comparator<WorkoutSet>
        get() {
            val amount = compareBy<WorkoutSet> { if (measure == Measure.Time) it.seconds ?: 0 else it.reps ?: 0 }
            return when (load) {
                Load.Weight -> if (measure == Measure.Reps) {
                    compareBy<WorkoutSet> { it.estimatedMax ?: 0.0 }.then(amount)
                } else {
                    compareBy<WorkoutSet> { it.weight ?: 0.0 }.then(amount)
                }
                Load.Bodyweight -> amount
                Load.ExtraWeight -> compareBy<WorkoutSet> { it.weight ?: 0.0 }.then(amount)
                Load.Assisted -> compareByDescending<WorkoutSet> { it.weight ?: 0.0 }.then(amount)
            }
        }
}

/** A built-in exercise: its key, and how it's measured. */
data class BuiltIn(
    val key: String,
    val muscle: Muscle,
    val measure: Measure = Measure.Reps,
    val load: Load = Load.Weight,
    val perSide: Boolean = false,
) {
    fun toExercise() = Exercise(key = key, muscle = muscle, measure = measure, load = load, perSide = perSide)
}

/** The exercises every library starts with. Adding one here adds it for existing users on the next launch. */
object BuiltInExercises {
    val all: List<BuiltIn> = listOf(
        BuiltIn("bench_press", Muscle.Chest),
        BuiltIn("incline_bench_press", Muscle.Chest),
        BuiltIn("dumbbell_bench_press", Muscle.Chest),
        BuiltIn("push_up", Muscle.Chest, load = Load.Bodyweight),
        BuiltIn("cable_fly", Muscle.Chest),
        BuiltIn("dip", Muscle.Chest, load = Load.ExtraWeight),
        BuiltIn("deadlift", Muscle.Back),
        BuiltIn("barbell_row", Muscle.Back),
        BuiltIn("pull_up", Muscle.Back, load = Load.ExtraWeight),
        BuiltIn("assisted_pull_up", Muscle.Back, load = Load.Assisted),
        BuiltIn("lat_pulldown", Muscle.Back),
        BuiltIn("seated_cable_row", Muscle.Back),
        BuiltIn("one_arm_dumbbell_row", Muscle.Back, perSide = true),
        BuiltIn("squat", Muscle.Legs),
        BuiltIn("front_squat", Muscle.Legs),
        BuiltIn("romanian_deadlift", Muscle.Legs),
        BuiltIn("leg_press", Muscle.Legs),
        BuiltIn("leg_curl", Muscle.Legs),
        BuiltIn("leg_extension", Muscle.Legs),
        BuiltIn("bulgarian_split_squat", Muscle.Legs, perSide = true),
        BuiltIn("hip_thrust", Muscle.Legs),
        BuiltIn("calf_raise", Muscle.Legs),
        BuiltIn("overhead_press", Muscle.Shoulders),
        BuiltIn("dumbbell_shoulder_press", Muscle.Shoulders),
        BuiltIn("lateral_raise", Muscle.Shoulders),
        BuiltIn("face_pull", Muscle.Shoulders),
        BuiltIn("barbell_curl", Muscle.Arms),
        BuiltIn("dumbbell_curl", Muscle.Arms),
        BuiltIn("hammer_curl", Muscle.Arms),
        BuiltIn("triceps_pushdown", Muscle.Arms),
        BuiltIn("skull_crusher", Muscle.Arms),
        BuiltIn("hanging_leg_raise", Muscle.Core, load = Load.Bodyweight),
        BuiltIn("cable_crunch", Muscle.Core),
        BuiltIn("ab_wheel", Muscle.Core, load = Load.Bodyweight),
        BuiltIn("plank", Muscle.Core, measure = Measure.Time, load = Load.Bodyweight),
        BuiltIn("side_plank", Muscle.Core, measure = Measure.Time, load = Load.Bodyweight, perSide = true),
    )
}
