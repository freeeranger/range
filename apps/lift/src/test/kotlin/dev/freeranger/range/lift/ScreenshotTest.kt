package dev.freeranger.range.lift

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.freeranger.range.lift.data.BuiltInExercises
import dev.freeranger.range.lift.data.Exercise
import dev.freeranger.range.lift.data.Muscle
import dev.freeranger.range.lift.data.Workout
import dev.freeranger.range.lift.data.WorkoutExercise
import dev.freeranger.range.lift.data.WorkoutSet
import dev.freeranger.range.lift.data.bestSets
import dev.freeranger.range.lift.data.previousSets
import dev.freeranger.range.lift.data.workoutLogs
import dev.freeranger.range.lift.ui.exercise.ExerciseDetailScreen
import dev.freeranger.range.lift.ui.exercise.ExerciseDetailState
import dev.freeranger.range.lift.ui.exercise.ExerciseEditScreen
import dev.freeranger.range.lift.ui.exercise.ExercisePickerScreen
import dev.freeranger.range.lift.ui.exercise.Session
import dev.freeranger.range.lift.ui.home.HomeActions
import dev.freeranger.range.lift.ui.home.HomeScreen
import dev.freeranger.range.lift.ui.home.HomeState
import dev.freeranger.range.lift.ui.home.HomeTab
import dev.freeranger.range.lift.ui.workout.WorkoutActions
import dev.freeranger.range.lift.ui.workout.WorkoutDialog
import dev.freeranger.range.lift.ui.workout.WorkoutScreen
import dev.freeranger.range.lift.ui.workout.WorkoutState
import dev.freeranger.range.ui.settings.RangeAboutScreen
import dev.freeranger.range.ui.settings.RangeAppIcon
import dev.freeranger.range.ui.settings.RangeAppearance
import dev.freeranger.range.ui.settings.RangeLicense
import dev.freeranger.range.ui.settings.RangeLicenseScreen
import dev.freeranger.range.ui.settings.RangeSettingsScreen
import dev.freeranger.range.ui.settings.ThemeMode
import dev.freeranger.range.ui.theme.RangeAccents
import dev.freeranger.range.ui.theme.RangeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

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
        Workout(3, "Push", at(friday.minusDays(1).atTime(17, 10)), at(friday.minusDays(1).atTime(18, 2))),
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
    private val active = logs.first { !it.workout.isFinished }
    private val activeState = WorkoutState(active, active.exercises.associate { it.entry.id to previousSets(logs, it.exercise.id, active.workout.startedAt) })
    private val finishedState = WorkoutState(logs.first { it.workout.id == 3L })

    private val benchDetail = ExerciseDetailState(
        exercise = exercises.first { it.key == "bench_press" },
        sessions = logs.filter { it.workout.isFinished }.mapNotNull { log ->
            log.exercises.firstOrNull { it.exercise.key == "bench_press" }?.let { Session(log.workout, it.sets) }
        },
        best = bestSets(logs)[id("bench_press")],
    )

    @Test
    fun home() = capture { HomeScreen(home, now, HomeActions()) }

    @Test
    @Config(qualifiers = "sv-rSE-$PHONE")
    fun homeSwedish() = capture { HomeScreen(home, now, HomeActions()) }

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
    fun workoutSetsLeft() = capture(
        then = {
            compose.onNodeWithText("Finish").performClick()
            compose.waitForIdle()
        },
    ) { WorkoutScreen(activeState, now, WorkoutActions()) }

    @Test
    fun workoutRenaming() = capture { WorkoutScreen(activeState, now, WorkoutActions(), initialDialog = WorkoutDialog.Rename) }

    @Test
    fun workoutFinished() = capture { WorkoutScreen(finishedState, now, WorkoutActions()) }

    @Test
    fun exercisePicker() = capture { ExercisePickerScreen(exercises, bestSets(logs), onPick = {}, onCreate = {}, onBack = {}) }

    @Test
    fun exerciseDetail() = capture { ExerciseDetailScreen(benchDetail, friday, onBack = {}) }

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
    fun settings() = capture { RangeSettingsScreen(RangeAppearance(), onAppearanceChange = {}, onBack = {}, onOpenAbout = {}) }

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
        then: () -> Unit = {},
        content: @Composable () -> Unit,
    ) {
        compose.setContent { RangeTheme(accent = RangeAccents.Lift, appearance = appearance, content = content) }
        compose.waitForIdle()
        then()
        captureScreenRoboImage()
    }
}

private const val PHONE = "w412dp-h915dp-night-xxhdpi"
