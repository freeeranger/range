package dev.freeranger.range.lift.ui.common

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import dev.freeranger.range.lift.R
import dev.freeranger.range.lift.data.Exercise
import dev.freeranger.range.lift.data.Load
import dev.freeranger.range.lift.data.Measure
import dev.freeranger.range.lift.data.Muscle
import dev.freeranger.range.lift.data.Workout
import dev.freeranger.range.lift.data.WorkoutSet
import kotlinx.coroutines.delay
import java.text.Collator
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/** Turns dates, weights, sets and names into the text shown on screen. */
class LiftFormat(context: Context) {
    private val res = context.resources

    /**
     * The app's language with the phone's region: English words with the date order, clock and
     * decimal mark people expect where they live ("62,5 kg" in Sweden, "62.5 kg" in the US).
     */
    val locale: Locale = Locale.Builder()
        .setLanguage(res.getString(R.string.format_language))
        .setRegion(res.configuration.locales[0].country)
        .build()

    val zone: ZoneId get() = ZoneId.systemDefault()

    private val timeFormat = pattern(if (DateFormat.is24HourFormat(context)) "Hm" else "hma")
    private val dateFormat = pattern("EEEEdMMMM")
    private val dateYearFormat = pattern("EEEEdMMMMy")
    private val monthFormat = pattern("LLLLy")
    private val shortDateFormat = pattern("MMMd")
    private val weightFormat = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }
    private val volumeFormat = NumberFormat.getIntegerInstance(locale)
    private val collator = Collator.getInstance(locale)

    /** The week starts on the day people expect where they live: Monday in Sweden, Sunday in the US. */
    val firstDayOfWeek: DayOfWeek = WeekFields.of(locale).firstDayOfWeek

    /** Sorts names the way this language does, so "Ö" comes after "Z" in Swedish. */
    val byName: Comparator<String> = Comparator { a, b -> collator.compare(a, b) }

    fun name(exercise: Exercise): String = exercise.name.ifEmpty { builtInName(exercise).orEmpty() }

    /** A built-in exercise's name in the app's language, whatever the user renamed it to. Null for the user's own. */
    fun builtInName(exercise: Exercise): String? = exercise.key?.let(BUILT_IN_NAMES::get)?.let(res::getString)

    fun name(workout: Workout): String = workout.name.ifEmpty { res.getString(R.string.workout_default_name) }

    fun muscle(muscle: Muscle): String = res.getString(
        when (muscle) {
            Muscle.Chest -> R.string.muscle_chest
            Muscle.Back -> R.string.muscle_back
            Muscle.Legs -> R.string.muscle_legs
            Muscle.Shoulders -> R.string.muscle_shoulders
            Muscle.Arms -> R.string.muscle_arms
            Muscle.Core -> R.string.muscle_core
        },
    )

    fun measure(measure: Measure): String = res.getString(
        when (measure) {
            Measure.Reps -> R.string.measure_reps
            Measure.Time -> R.string.measure_time
        },
    )

    fun load(load: Load): String = res.getString(
        when (load) {
            Load.Weight -> R.string.load_weight
            Load.Bodyweight -> R.string.load_bodyweight
            Load.ExtraWeight -> R.string.load_extra_weight
            Load.Assisted -> R.string.load_assisted
        },
    )

    /** "Bodyweight · Time · Each side": anything unusual about how the exercise is logged. Null for weight and reps. */
    fun kind(exercise: Exercise): String? = listOfNotNull(
        load(exercise.load).takeIf { exercise.load != Load.Weight },
        measure(Measure.Time).takeIf { exercise.measure == Measure.Time },
        res.getString(R.string.exercise_each_side).takeIf { exercise.perSide },
    ).joinToString(" · ").ifEmpty { null }

    /** "Legs · Each side": what the exercise trains, and how it's logged if that's unusual. */
    fun about(exercise: Exercise): String = listOfNotNull(muscle(exercise.muscle), kind(exercise)).joinToString(" · ")

    /** The weight column's heading: "kg", "+kg" for extra weight, "−kg" for help taken off. */
    fun weightLabel(load: Load): String = res.getString(
        when (load) {
            Load.Assisted -> R.string.unit_kg_assist_label
            Load.ExtraWeight -> R.string.unit_kg_extra_label
            else -> R.string.unit_kg_label
        },
    )

    fun time(at: Instant): String = at.atZone(zone).format(timeFormat)

    /** "Today", "Yesterday", else "Thursday 22 October", with the year when it isn't this one. Capitalised. */
    fun day(at: Instant, today: LocalDate): String {
        val date = at.atZone(zone).toLocalDate()
        return when {
            date == today -> res.getString(R.string.date_today)
            date == today.minusDays(1) -> res.getString(R.string.date_yesterday)
            date.year == today.year -> date.format(dateFormat)
            else -> date.format(dateYearFormat)
        }.capitalized()
    }

    /** "Oct 12": a date on a chart's axis. */
    fun shortDate(date: LocalDate): String = date.format(shortDateFormat)

    /** "October 2026", heading a month of workouts. */
    fun month(date: LocalDate): String = date.format(monthFormat).capitalized()

    fun duration(from: Instant, to: Instant): String {
        val minutes = Duration.between(from, to).toMinutes().coerceAtLeast(0)
        return if (minutes < 60) {
            res.getString(R.string.duration_minutes, minutes.toInt())
        } else {
            res.getString(R.string.duration_hours, (minutes / 60).toInt(), (minutes % 60).toInt())
        }
    }

    /** "01:05:09": how long a workout has been going, as a stopwatch shows it. */
    fun stopwatch(from: Instant, to: Instant): String {
        val seconds = Duration.between(from, to).seconds.coerceAtLeast(0)
        return "%02d:%02d:%02d".format(locale, seconds / 3600, seconds / 60 % 60, seconds % 60)
    }

    /** "0:45", "1:30" or "1:02:00": a timed set. */
    fun seconds(seconds: Int): String = if (seconds >= 3600) {
        "%d:%02d:%02d".format(locale, seconds / 3600, seconds / 60 % 60, seconds % 60)
    } else {
        "%d:%02d".format(locale, seconds / 60, seconds % 60)
    }

    /** "62.5" or "62,5": a weight without its unit, as typed into a set. */
    fun number(kg: Double): String = weightFormat.format(kg)

    fun kg(kg: Double): String = res.getString(R.string.unit_kg, number(kg))

    /** "4,320 kg" moved in a workout. */
    fun volume(kg: Double): String = res.getString(R.string.unit_kg, volumeFormat.format(kg))

    /** A weight as [load] means it: "80 kg", "+10 kg" extra, or "−20 kg" of help. */
    fun weight(kg: Double, load: Load): String = when (load) {
        Load.ExtraWeight -> res.getString(R.string.unit_kg_extra, number(kg))
        Load.Assisted -> res.getString(R.string.unit_kg_assist, number(kg))
        else -> kg(kg)
    }

    /** "80 kg × 5", "8 reps", "+10 kg × 8", "1:30" or "20 kg × 0:45", as the exercise measures it. */
    fun set(set: WorkoutSet, exercise: Exercise): String {
        val weight = set.weight?.takeIf { it > 0 && exercise.weighted }
        val amount = when (exercise.measure) {
            Measure.Reps -> set.reps?.toString()
            Measure.Time -> set.seconds?.let(::seconds)
        }
        return when {
            weight == null && exercise.measure == Measure.Reps -> reps(set.reps ?: 0)
            weight == null -> amount.orEmpty()
            amount == null -> weight(weight, exercise.load)
            else -> res.getString(R.string.set_weight_amount, weight(weight, exercise.load), amount)
        }
    }

    /** "8 reps". */
    fun reps(count: Int): String = res.getQuantityString(R.plurals.set_reps, count, count)

    /** "93 kg · Yesterday": a value and what it belongs to. */
    fun labelled(value: String, label: String): String = res.getString(R.string.value_label, value, label)

    fun sets(count: Int): String = res.getQuantityString(R.plurals.sets_count, count, count)

    /** A typed weight, with either decimal mark. Null if it isn't a number. */
    fun parseWeight(text: String): Double? = text.replace(',', '.').toDoubleOrNull()

    // Swedish writes weekdays and months in lowercase, even at the start of a line.
    private fun String.capitalized() = replaceFirstChar { it.titlecase(locale) }

    private fun pattern(skeleton: String): DateTimeFormatter =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

    companion object {
        /** The name of each built-in exercise, by key. */
        val BUILT_IN_NAMES: Map<String, Int> = mapOf(
            "bench_press" to R.string.exercise_bench_press,
            "incline_bench_press" to R.string.exercise_incline_bench_press,
            "dumbbell_bench_press" to R.string.exercise_dumbbell_bench_press,
            "push_up" to R.string.exercise_push_up,
            "cable_fly" to R.string.exercise_cable_fly,
            "dip" to R.string.exercise_dip,
            "deadlift" to R.string.exercise_deadlift,
            "barbell_row" to R.string.exercise_barbell_row,
            "pull_up" to R.string.exercise_pull_up,
            "lat_pulldown" to R.string.exercise_lat_pulldown,
            "seated_cable_row" to R.string.exercise_seated_cable_row,
            "squat" to R.string.exercise_squat,
            "front_squat" to R.string.exercise_front_squat,
            "romanian_deadlift" to R.string.exercise_romanian_deadlift,
            "leg_press" to R.string.exercise_leg_press,
            "leg_curl" to R.string.exercise_leg_curl,
            "leg_extension" to R.string.exercise_leg_extension,
            "bulgarian_split_squat" to R.string.exercise_bulgarian_split_squat,
            "hip_thrust" to R.string.exercise_hip_thrust,
            "calf_raise" to R.string.exercise_calf_raise,
            "overhead_press" to R.string.exercise_overhead_press,
            "dumbbell_shoulder_press" to R.string.exercise_dumbbell_shoulder_press,
            "lateral_raise" to R.string.exercise_lateral_raise,
            "face_pull" to R.string.exercise_face_pull,
            "barbell_curl" to R.string.exercise_barbell_curl,
            "dumbbell_curl" to R.string.exercise_dumbbell_curl,
            "hammer_curl" to R.string.exercise_hammer_curl,
            "triceps_pushdown" to R.string.exercise_triceps_pushdown,
            "skull_crusher" to R.string.exercise_skull_crusher,
            "hanging_leg_raise" to R.string.exercise_hanging_leg_raise,
            "cable_crunch" to R.string.exercise_cable_crunch,
            "ab_wheel" to R.string.exercise_ab_wheel,
            "assisted_pull_up" to R.string.exercise_assisted_pull_up,
            "one_arm_dumbbell_row" to R.string.exercise_one_arm_dumbbell_row,
            "plank" to R.string.exercise_plank,
            "side_plank" to R.string.exercise_side_plank,
        )
    }
}

/** A [LiftFormat] that refreshes when the phone's language, region or clock setting changes. */
@Composable
fun rememberLiftFormat(): LiftFormat {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { LiftFormat(context) }
}

/** The current time, updated each [period] (a minute unless a stopwatch needs seconds), on the dot. */
@Composable
fun rememberNow(period: Long = 60_000): Instant {
    val now by produceState(Instant.now(), period) {
        while (true) {
            value = Instant.now()
            delay(period - value.toEpochMilli() % period + 5)
        }
    }
    return now
}
