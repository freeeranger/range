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
    /** How long to rest after each set, started when a set is ticked off. 0 turns the timer off. */
    @ColumnInfo(defaultValue = "120") val restSeconds: Int = DEFAULT_REST,
) {
    companion object {
        const val DEFAULT_REST = 120

        /** The rest times offered when editing an exercise. */
        val REST_CHOICES = listOf(0, 60, 90, 120, 180, 300)
    }

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
    /** Heavy compound lifts get longer between sets than small isolation work. */
    val restSeconds: Int = Exercise.DEFAULT_REST,
) {
    fun toExercise() = Exercise(key = key, muscle = muscle, measure = measure, load = load, perSide = perSide, restSeconds = restSeconds)
}

private const val HEAVY = 180
private const val LIGHT = 90

/** The exercises every library starts with. Adding one here adds it for existing users on the next launch. */
object BuiltInExercises {
    val all: List<BuiltIn> = listOf(
        BuiltIn("bench_press", Muscle.Chest, restSeconds = HEAVY),
        BuiltIn("incline_bench_press", Muscle.Chest, restSeconds = HEAVY),
        BuiltIn("dumbbell_bench_press", Muscle.Chest),
        BuiltIn("push_up", Muscle.Chest, load = Load.Bodyweight),
        BuiltIn("cable_fly", Muscle.Chest, restSeconds = LIGHT),
        BuiltIn("dip", Muscle.Chest, load = Load.ExtraWeight),
        BuiltIn("deadlift", Muscle.Back, restSeconds = HEAVY),
        BuiltIn("barbell_row", Muscle.Back, restSeconds = HEAVY),
        BuiltIn("pull_up", Muscle.Back, load = Load.ExtraWeight),
        BuiltIn("assisted_pull_up", Muscle.Back, load = Load.Assisted),
        BuiltIn("lat_pulldown", Muscle.Back),
        BuiltIn("seated_cable_row", Muscle.Back),
        BuiltIn("one_arm_dumbbell_row", Muscle.Back, perSide = true),
        BuiltIn("squat", Muscle.Legs, restSeconds = HEAVY),
        BuiltIn("front_squat", Muscle.Legs, restSeconds = HEAVY),
        BuiltIn("romanian_deadlift", Muscle.Legs, restSeconds = HEAVY),
        BuiltIn("leg_press", Muscle.Legs, restSeconds = HEAVY),
        BuiltIn("leg_curl", Muscle.Legs, restSeconds = LIGHT),
        BuiltIn("leg_extension", Muscle.Legs, restSeconds = LIGHT),
        BuiltIn("bulgarian_split_squat", Muscle.Legs, perSide = true),
        BuiltIn("hip_thrust", Muscle.Legs, restSeconds = HEAVY),
        BuiltIn("calf_raise", Muscle.Legs, restSeconds = LIGHT),
        BuiltIn("overhead_press", Muscle.Shoulders, restSeconds = HEAVY),
        BuiltIn("dumbbell_shoulder_press", Muscle.Shoulders),
        BuiltIn("lateral_raise", Muscle.Shoulders, restSeconds = LIGHT),
        BuiltIn("face_pull", Muscle.Shoulders, restSeconds = LIGHT),
        BuiltIn("barbell_curl", Muscle.Arms, restSeconds = LIGHT),
        BuiltIn("dumbbell_curl", Muscle.Arms, restSeconds = LIGHT),
        BuiltIn("hammer_curl", Muscle.Arms, restSeconds = LIGHT),
        BuiltIn("triceps_pushdown", Muscle.Arms, restSeconds = LIGHT),
        BuiltIn("skull_crusher", Muscle.Arms, restSeconds = LIGHT),
        BuiltIn("hanging_leg_raise", Muscle.Core, load = Load.Bodyweight, restSeconds = LIGHT),
        BuiltIn("cable_crunch", Muscle.Core, restSeconds = LIGHT),
        BuiltIn("ab_wheel", Muscle.Core, load = Load.Bodyweight, restSeconds = LIGHT),
        BuiltIn("plank", Muscle.Core, measure = Measure.Time, load = Load.Bodyweight, restSeconds = LIGHT),
        BuiltIn("side_plank", Muscle.Core, measure = Measure.Time, load = Load.Bodyweight, perSide = true, restSeconds = LIGHT),
    )
}
