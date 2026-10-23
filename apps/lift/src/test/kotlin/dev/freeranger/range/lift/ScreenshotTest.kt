package dev.freeranger.range.lift

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.freeranger.range.lift.data.BuiltInExercises
import dev.freeranger.range.lift.data.Exercise
import dev.freeranger.range.lift.data.LoggedExercise
import dev.freeranger.range.lift.data.Muscle
import dev.freeranger.range.lift.data.Template
import dev.freeranger.range.lift.data.Workout
import dev.freeranger.range.lift.data.WorkoutExercise
import dev.freeranger.range.lift.data.WorkoutLog
import dev.freeranger.range.lift.data.WorkoutSet
import dev.freeranger.range.lift.data.WorkoutTemplate
import dev.freeranger.range.lift.data.bestSets
import dev.freeranger.range.lift.data.comparisons
import dev.freeranger.range.lift.data.newRecords
import dev.freeranger.range.lift.data.previousSets
import dev.freeranger.range.lift.data.progression
import dev.freeranger.range.lift.data.recentExercises
import dev.freeranger.range.lift.data.records
import dev.freeranger.range.lift.data.workoutLogs
import dev.freeranger.range.lift.ui.exercise.ExerciseDetailScreen
import dev.freeranger.range.lift.ui.exercise.ExerciseDetailState
import dev.freeranger.range.lift.ui.exercise.ExerciseEditScreen
import dev.freeranger.range.lift.ui.exercise.ExercisePickerScreen
import dev.freeranger.range.lift.ui.exercise.PickerState
import dev.freeranger.range.lift.ui.exercise.Session
import dev.freeranger.range.lift.ui.home.HomeActions
import dev.freeranger.range.lift.ui.home.HomeScreen
import dev.freeranger.range.lift.ui.home.HomeState
import dev.freeranger.range.lift.ui.home.HomeTab
import dev.freeranger.range.lift.ui.settings.LiftSettingsScreen
import dev.freeranger.range.lift.ui.workout.SummaryScreen
import dev.freeranger.range.lift.ui.workout.SummaryState
import dev.freeranger.range.lift.ui.workout.WorkoutActions
import dev.freeranger.range.lift.ui.workout.WorkoutDialog
import dev.freeranger.range.lift.ui.workout.WorkoutScreen
import dev.freeranger.range.lift.ui.workout.WorkoutState
import dev.freeranger.range.ui.settings.RangeAboutScreen
import dev.freeranger.range.ui.settings.RangeAppIcon
import dev.freeranger.range.ui.settings.RangeAppearance
import dev.freeranger.range.ui.settings.RangeLicense
import dev.freeranger.range.ui.settings.RangeLicenseScreen
import dev.freeranger.range.ui.settings.ThemeMode
import dev.freeranger.range.ui.theme.RangeAccents
import dev.freeranger.range.ui.theme.RangeTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every screen on the JVM, dialogs included.
 * `./gradlew :apps:lift:recordRoborazziDebug` updates apps/lift/screenshots.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE)
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val friday = LocalDate.of(2026, 10, 23)
    private fun at(time: LocalDateTime): Instant = time.atZone(ZoneId.systemDefault()).toInstant()
    private val now = at(friday.atTime(18, 40))

    private val exercises = BuiltInExercises.all.mapIndexed { i, builtIn -> builtIn.toExercise().copy(id = i + 1L) } +
        Exercise(id = 100, name = "Landmine press", muscle = Muscle.Shoulders, perSide = true)
    private fun id(key: String) = exercises.first { it.key == key }.id

    private val workouts = listOf(
        Workout(1, "Push", at(friday.minusDays(17).atTime(17, 30)), at(friday.minusDays(17).atTime(18, 25))),
        Workout(2, "Legs", at(friday.minusDays(3).atTime(17, 5)), at(friday.minusDays(3).atTime(18, 12))),
        Workout(3, "Push", at(friday.minusDays(1).atTime(17, 10)), at(friday.minusDays(1).atTime(18, 2)), notes = "Bench moved fast. Dips felt heavy on the elbows, go easier next time."),
        Workout(4, "", at(friday.atTime(18, 5))),
    )
    private val entries = listOf(
        WorkoutExercise(1, 1, id("bench_press"), 0),
        WorkoutExercise(2, 2, id("squat"), 0),
        WorkoutExercise(3, 2, id("romanian_deadlift"), 1),
        WorkoutExercise(4, 3, id("bench_press"), 0),
        WorkoutExercise(5, 3, id("overhead_press"), 1),
        WorkoutExercise(6, 3, id("dip"), 2),
        WorkoutExercise(7, 4, id("bench_press"), 0),
        WorkoutExercise(8, 4, id("pull_up"), 1),
        WorkoutExercise(9, 4, id("plank"), 2),
    )
    private var nextSet = 1L
    private fun sets(entry: Long, vararg sets: Pair<Double?, Int>, done: Int = sets.size) =
        sets.mapIndexed { i, (kg, reps) -> WorkoutSet(nextSet++, entry, i, kg, reps, done = i < done) }
    private val sets = sets(1, 77.5 to 5, 77.5 to 5, 77.5 to 4) +
        sets(2, 100.0 to 5, 100.0 to 5, 100.0 to 5) +
        sets(3, 80.0 to 8, 80.0 to 8) +
        sets(4, 80.0 to 5, 80.0 to 5, 80.0 to 5) +
        sets(5, 47.5 to 6, 47.5 to 6, 45.0 to 7) +
        sets(6, null to 12, null to 10) +
        // The workout in progress: one set done of each, the rest empty with last time's numbers showing.
        listOf(
            WorkoutSet(nextSet++, 7, 0, 82.5, 5, done = true),
            WorkoutSet(nextSet++, 7, 1),
            WorkoutSet(nextSet++, 7, 2),
            WorkoutSet(nextSet++, 8, 0, 5.0, 8, done = true),
            WorkoutSet(nextSet++, 8, 1),
            WorkoutSet(nextSet++, 9, 0, seconds = 75, done = true),
            WorkoutSet(nextSet++, 9, 1),
        )
    private val logs = workoutLogs(workouts, entries, sets, exercises)
    private val home = HomeState(logs, exercises, loaded = true)

    /** Three months of two workouts a week, getting stronger, with a week off, for the dashboard's charts. */
    private val progress: List<WorkoutLog> = buildList {
        var id = 1000L
        fun log(day: LocalDate, name: String, vararg lifts: Pair<String, List<Pair<Double, Int>>>) {
            val workout = Workout(id++, name, at(day.atTime(17, 30)), at(day.atTime(18, 35)), templateId = if (name == "Push") 1 else 2)
            val logged = lifts.mapIndexed { position, (key, sets) ->
                val entry = WorkoutExercise(id++, workout.id, id(key), position)
                LoggedExercise(entry, exercises.first { it.key == key }, sets.mapIndexed { i, (kg, reps) -> WorkoutSet(id++, entry.id, i, kg, reps, done = true) })
            }
            add(WorkoutLog(workout, logged))
        }
        for (week in 0..11) {
            if (week == 7) continue
            val monday = friday.minusWeeks(11L - week).with(java.time.DayOfWeek.MONDAY)
            log(monday, "Push", "bench_press" to List(3) { 60.0 + 2.5 * week to 5 }, "overhead_press" to List(3) { 35.0 + week to 6 }, "dip" to List(2) { 0.0 to 10 })
            if (monday.plusDays(3) < friday) {
                log(monday.plusDays(3), "Legs", "squat" to List(3) { 80.0 + 5 * week to 5 }, "romanian_deadlift" to List(2) { 70.0 + 2.5 * week to 8 }, "pull_up" to List(3) { 0.0 to 8 + week / 3 })
            }
        }
    }.sortedByDescending { it.workout.startedAt }
    private fun template(id: Long, name: String, vararg lifts: Pair<String, Int>) =
        WorkoutTemplate(Template(id, name, Instant.EPOCH), lifts.map { (key, sets) -> exercises.first { it.key == key } to sets })
    private val templates = listOf(
        template(1, "Push", "bench_press" to 3, "overhead_press" to 3, "dip" to 2),
        template(2, "Legs", "squat" to 3, "romanian_deadlift" to 2, "pull_up" to 3),
    )
    private val dashboard = HomeState(progress, exercises, templates, loaded = true)

    /** The last workout of the three months, just finished: records, and a step up on everything. */
    private val justFinished = progress.first()
    private fun summaryOf(template: WorkoutTemplate? = null) = SummaryState(
        log = justFinished,
        records = newRecords(justFinished, progress),
        comparisons = comparisons(justFinished, progress),
        logs = progress,
        template = template,
    )
    private val active = logs.first { !it.workout.isFinished }
    private val activeState = WorkoutState(
        active,
        active.exercises.associate { it.entry.id to previousSets(logs, it.exercise.id, active.workout.startedAt) },
        newRecords(active, logs),
    )
    private val finishedState = logs.first { it.workout.id == 3L }.let { WorkoutState(it, records = newRecords(it, logs)) }

    private val benchDetail = ExerciseDetailState(
        exercise = exercises.first { it.key == "bench_press" },
        sessions = logs.filter { it.workout.isFinished }.mapNotNull { log ->
            log.exercises.firstOrNull { it.exercise.key == "bench_press" }?.let { Session(log.workout, it.sets) }
        },
        best = bestSets(logs)[id("bench_press")],
        progression = progression(logs, exercises.first { it.key == "bench_press" }),
        records = records(logs, exercises.first { it.key == "bench_press" }),
    )

    /** An exercise's page with three months of history behind it. */
    private fun detail(key: String): ExerciseDetailState {
        val exercise = exercises.first { it.key == key }
        val sessions = progress.mapNotNull { log -> log.exercises.firstOrNull { it.exercise.id == exercise.id }?.let { Session(log.workout, it.sets) } }
        return ExerciseDetailState(exercise, sessions, bestSets(progress)[exercise.id], progression(progress, exercise), records(progress, exercise))
    }

    @Test
    fun home() = capture { HomeScreen(dashboard, now, HomeActions()) }

    @Test
    @Config(qualifiers = "sv-rSE-$PHONE")
    fun homeSwedish() = capture { HomeScreen(dashboard, now, HomeActions()) }

    @Test
    @Config(qualifiers = "+notnight")
    fun homeLight() = capture(RangeAppearance(theme = ThemeMode.Light, dynamicColor = false)) { HomeScreen(dashboard, now, HomeActions()) }


    @Test
    fun workouts() = capture { HomeScreen(home, now, HomeActions(), initialTab = HomeTab.Workouts) }

    @Test
    @Config(qualifiers = "sv-rSE-$PHONE")
    fun workoutsSwedish() = capture { HomeScreen(home, now, HomeActions(), initialTab = HomeTab.Workouts) }

    @Test
    fun homeBusy() = capture { HomeScreen(dashboard.copy(logs = listOf(active) + progress), now, HomeActions()) }

    @Test
    fun summary() = capture { SummaryScreen(summaryOf(), now, {}, {}, {}, {}) }

    /** Tall, to show the notes and the template's Update button below the comparisons. */
    @Test
    @Config(qualifiers = "sv-rSE-w412dp-h1500dp-night-xxhdpi")
    fun summarySwedish() = capture { SummaryScreen(summaryOf(templates[1].copy(exercises = templates[1].exercises.dropLast(1))), now, {}, {}, {}, {}) }

    @Test
    fun summarySaving() = capture { SummaryScreen(summaryOf(), now, {}, {}, {}, {}, initiallySaving = true) }

    @Test
    fun workoutResting() = capture {
        val resting = active.copy(workout = active.workout.copy(lastSetAt = now.minusSeconds(83)))
        WorkoutScreen(activeState.copy(log = resting), now, WorkoutActions())
    }

    @Test
    fun workoutEditing() = capture { WorkoutScreen(finishedState, now, WorkoutActions(), initiallyEditing = true) }

    @Test
    fun workoutNotes() = capture { WorkoutScreen(finishedState, now, WorkoutActions(), initialDialog = WorkoutDialog.Notes) }

    @Test
    fun homeEmpty() = capture { HomeScreen(HomeState(exercises = exercises, loaded = true), now, HomeActions()) }

    @Test
    fun exercises() = capture { HomeScreen(home, now, HomeActions(), initialTab = HomeTab.Exercises) }

    @Test
    @Config(qualifiers = "sv-rSE-$PHONE")
    fun exercisesSwedish() = capture { HomeScreen(home, now, HomeActions(), initialTab = HomeTab.Exercises) }

    @Test
    fun newExercise() = capture { ExerciseEditScreen(exercise = null, onSave = {}, onCancel = {}, autoFocus = false) }

    @Test
    fun editExercise() = capture { ExerciseEditScreen(exercises.first { it.key == "pull_up" }, onSave = {}, onCancel = {}) }

    @Test
    @Config(qualifiers = "sv-rSE-$PHONE")
    fun editExerciseSwedish() = capture { ExerciseEditScreen(exercises.first { it.key == "side_plank" }, onSave = {}, onCancel = {}) }

    @Test
    fun workout() = capture { WorkoutScreen(activeState, now, WorkoutActions()) }

    @Test
    @Config(qualifiers = "sv-rSE-$PHONE")
    fun workoutSwedish() = capture { WorkoutScreen(activeState, now, WorkoutActions()) }

    @Test
    @Config(qualifiers = "+notnight")
    fun workoutLight() = capture(RangeAppearance(theme = ThemeMode.Light, dynamicColor = false)) { WorkoutScreen(activeState, now, WorkoutActions()) }

    @Test
    fun workoutPureBlack() = capture(RangeAppearance(dynamicColor = false, pureBlack = true)) { WorkoutScreen(activeState, now, WorkoutActions()) }

    @Test
    fun workoutJustStarted() = capture {
        WorkoutScreen(WorkoutState(active.copy(exercises = emptyList())), now, WorkoutActions())
    }

    @Test
    fun workoutAllTicked() = capture {
        val allDone = active.copy(exercises = active.exercises.map { e -> e.copy(sets = e.sets.map { it.copy(done = true, reps = it.reps ?: 8, seconds = it.seconds ?: 60) }) })
        WorkoutScreen(WorkoutState(allDone), now, WorkoutActions())
    }

    @Test
    fun workoutRenaming() = capture { WorkoutScreen(activeState, now, WorkoutActions(), initialDialog = WorkoutDialog.Rename) }

    @Test
    fun workoutFinished() = capture { WorkoutScreen(finishedState, now, WorkoutActions()) }

    @Test
    fun exercisePicker() = capture { ExercisePickerScreen(PickerState(exercises, bestSets(logs), recentExercises(logs)), onPick = {}, onCreate = {}, onBack = {}, initialSelection = listOf(id("bench_press"), id("dip"))) }

    @Test
    fun exerciseDetail() = capture { ExerciseDetailScreen(benchDetail, friday, onBack = {}) }

    @Test
    fun exerciseProgress() = capture { ExerciseDetailScreen(detail("squat"), friday, onBack = {}) }

    @Test
    @Config(qualifiers = "+notnight")
    fun exerciseProgressLight() = capture(RangeAppearance(theme = ThemeMode.Light, dynamicColor = false)) {
        ExerciseDetailScreen(detail("bench_press"), friday, onBack = {})
    }

    @Test
    @Config(qualifiers = "sv-rSE-$PHONE")
    fun exerciseProgressReps() = capture { ExerciseDetailScreen(detail("pull_up"), friday, onBack = {}) }

    @Test
    fun exerciseDetailRemoving() = capture { ExerciseDetailScreen(benchDetail, friday, onBack = {}, initiallyRemoving = true) }

    @Test
    fun exerciseDetailEmpty() = capture { ExerciseDetailScreen(ExerciseDetailState(exercises.first { it.key == "face_pull" }), friday, onBack = {}) }

    @Test
    fun icon() = capture {
        Row(Modifier.padding(32.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            RangeAppIcon(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground, size = 160.dp)
            RangeAppIcon(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground, size = 64.dp)
        }
    }

    @Test
    fun settings() = capture { LiftSettingsScreen(RangeAppearance(), onAppearanceChange = {}, onBack = {}, onOpenAbout = {}, onExport = {}, onRestore = {}) }

    @Test
    fun about() = capture {
        RangeAboutScreen(
            appName = "Range Lift",
            iconBackground = R.drawable.ic_launcher_background,
            iconForeground = R.drawable.ic_launcher_foreground,
            onBack = {},
            onOpenLicense = {},
            onOpenComponents = {},
        )
    }

    @Test
    fun license() = capture { RangeLicenseScreen(RangeLicense.Gpl3, onBack = {}) }

    /** Material You is off so screenshots don't depend on a wallpaper. */
    private fun capture(
        appearance: RangeAppearance = RangeAppearance(dynamicColor = false),
        content: @Composable () -> Unit,
    ) {
        compose.setContent { RangeTheme(accent = RangeAccents.Lift, appearance = appearance, content = content) }
        compose.waitForIdle()
        captureScreenRoboImage()
    }
}

private const val PHONE = "w412dp-h915dp-night-xxhdpi"
