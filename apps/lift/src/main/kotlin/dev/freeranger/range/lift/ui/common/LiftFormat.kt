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
import dev.freeranger.range.lift.data.Muscle
import dev.freeranger.range.lift.data.Workout
import dev.freeranger.range.lift.data.WorkoutSet
import kotlinx.coroutines.delay
import java.text.Collator
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    private val weightFormat = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }
    private val volumeFormat = NumberFormat.getIntegerInstance(locale)
    private val collator = Collator.getInstance(locale)

    /** Sorts names the way this language does, so "Ö" comes after "Z" in Swedish. */
    val byName: Comparator<String> = Comparator { a, b -> collator.compare(a, b) }

    fun name(exercise: Exercise): String = exercise.key?.let(BUILT_IN_NAMES::get)?.let(res::getString) ?: exercise.name

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

    /** "62.5" or "62,5": a weight without its unit, as typed into a set. */
    fun number(kg: Double): String = weightFormat.format(kg)

    fun kg(kg: Double): String = res.getString(R.string.unit_kg, number(kg))

    /** "4,320 kg" moved in a workout. */
    fun volume(kg: Double): String = res.getString(R.string.unit_kg, volumeFormat.format(kg))

    /** "80 kg × 5", or "8 reps" for a bodyweight set. */
    fun set(set: WorkoutSet): String {
        val weight = set.weight?.takeIf { it > 0 }
        val reps = set.reps ?: 0
        return when {
            weight == null -> res.getQuantityString(R.plurals.set_reps, reps, reps)
            set.reps == null -> kg(weight)
            else -> res.getString(R.string.set_weight_reps, kg(weight), reps)
        }
    }

    /** "80 × 5": a set in the narrow Previous column. */
    fun setShort(set: WorkoutSet): String {
        val weight = set.weight?.takeIf { it > 0 }
        val reps = set.reps ?: 0
        return if (weight == null) res.getQuantityString(R.plurals.set_reps, reps, reps) else res.getString(R.string.set_weight_reps, number(weight), reps)
    }

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

/** The current time, updated each minute, so a running workout's duration stays right. */
@Composable
fun rememberNow(): Instant {
    val now by produceState(Instant.now()) {
        while (true) {
            value = Instant.now()
            delay(60_000 - value.toEpochMilli() % 60_000 + 50)
        }
    }
    return now
}
