package dev.freeranger.range.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.freeranger.range.tasks.data.Repeat
import dev.freeranger.range.tasks.data.RepeatEnd
import dev.freeranger.range.tasks.data.RepeatUnit
import dev.freeranger.range.tasks.data.Schedule
import dev.freeranger.range.tasks.data.Task
import dev.freeranger.range.tasks.data.check
import dev.freeranger.range.tasks.data.withSchedule
import dev.freeranger.range.tasks.ui.detail.TaskDetailActions
import dev.freeranger.range.tasks.ui.detail.TaskDetailScreen
import dev.freeranger.range.tasks.ui.list.TaskListActions
import dev.freeranger.range.tasks.ui.list.TaskListScreen
import dev.freeranger.range.tasks.ui.list.TaskListState
import dev.freeranger.range.tasks.ui.schedule.ScheduleSheet
import dev.freeranger.range.ui.components.RangeTimePickerDialog
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
import java.time.LocalTime
import java.time.ZoneId

/**
 * Renders every screen on the JVM, sheets and dialogs included.
 * `./gradlew :apps:tasks:recordRoborazziDebug` updates apps/tasks/screenshots.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE)
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val friday = LocalDate.of(2026, 10, 23)
    private val now = friday.atTime(10, 0)
    private val nowInstant = now.atZone(ZoneId.systemDefault()).toInstant()
    private val morning = LocalTime.of(8, 0)
    private val evening = LocalTime.of(20, 0)

    private fun task(id: Long, title: String, details: String = "", schedule: Schedule? = null) =
        Task(id = id, title = title, details = details, createdAt = Instant.EPOCH).withSchedule(schedule, friday.atStartOfDay())

    private val vitamins = task(2, "Take vitamins", "With breakfast and dinner", Schedule(friday, listOf(morning, evening), Repeat(anchor = friday)))
        .check(friday.atTime(8, 5), nowInstant)

    // Ticked off for this week already: on Sunday's occurrence, which can't be ticked yet.
    private val plants = task(4, "Water the plants", schedule = Schedule(friday.plusDays(2), emptyList(), Repeat(unit = RepeatUnit.Week, anchor = friday.plusDays(2))))

    private val state = TaskListState(
        tasks = listOf(
            task(1, "Pay rent", schedule = Schedule(friday.minusDays(1))),
            vitamins,
            task(3, "Book a dentist appointment", details = "Ask about the Tuesday slots", schedule = Schedule(friday.plusDays(1), listOf(LocalTime.of(9, 30)))),
            plants,
            task(5, "Buy oat milk"),
            task(6, "Stretch", schedule = Schedule(friday, listOf(LocalTime.of(7, 0)), Repeat(anchor = friday))).check(friday.atTime(7, 5), nowInstant),
            task(7, "Renew bike insurance").copy(completedAt = nowInstant),
        ),
        loaded = true,
    )

    @Composable
    private fun List(
        initiallyAdding: Boolean = false,
        initiallyShowingCompleted: Boolean = false,
        listState: TaskListState = state,
        day: LocalDate? = null,
    ) =
        TaskListScreen(
            state = listState,
            now = now,
            actions = TaskListActions(),
            initiallySelected = day,
            initiallyAdding = initiallyAdding,
            initiallyShowingCompleted = initiallyShowingCompleted,
        )

    @Test
    fun list() = capture { List() }

    @Test
    fun listCompletedOpen() = capture { List(initiallyShowingCompleted = true) }

    @Test
    @Config(qualifiers = "sv-$PHONE")
    fun listSwedish() = capture { List(initiallyShowingCompleted = true) }

    @Test
    fun listMonday() = capture { List(day = friday.plusDays(3)) }

    @Test
    @Config(qualifiers = "sv-$PHONE")
    fun listMondaySwedish() = capture { List(day = friday.plusDays(3)) }

    @Test
    fun listEmpty() = capture { List(listState = TaskListState(loaded = true)) }

    @Test
    fun newTask() = capture { List(initiallyAdding = true) }

    @Test
    fun scheduleRepeatingWeekly() = capture {
        List()
        ScheduleSheet(
            initial = Schedule(
                date = friday,
                times = listOf(LocalTime.of(7, 30), LocalTime.of(18, 0)),
                repeat = Repeat(unit = RepeatUnit.Week, weekdays = setOf(1, 3, 5), end = RepeatEnd.After(10), anchor = friday),
            ),
            today = friday,
            onDone = {},
            onDismiss = {},
            onClear = {},
        )
    }

    @Test
    @Config(qualifiers = "+notnight")
    fun listLight() = capture(RangeAppearance(theme = ThemeMode.Light, dynamicColor = false)) { List(initiallyShowingCompleted = true) }

    @Test
    fun listPureBlack() = capture(RangeAppearance(dynamicColor = false, pureBlack = true)) { List() }

    @Test
    fun icon() = capture {
        Row(Modifier.padding(32.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            RangeAppIcon(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground, size = 160.dp)
            RangeAppIcon(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground, size = 64.dp)
        }
    }

    @Test
    fun timePicker() = capture {
        List()
        RangeTimePickerDialog(LocalTime.of(20, 0), "Select time", "OK", "Cancel", onConfirm = {}, onDismiss = {})
    }

    @Test
    fun detail() = capture { TaskDetailScreen(vitamins, now, TaskDetailActions()) }

    @Test
    fun detailEditing() = capture { TaskDetailScreen(vitamins, now, TaskDetailActions(), initiallyEditing = true) }

    @Test
    fun detailLocked() = capture { TaskDetailScreen(plants, now, TaskDetailActions()) }

    @Test
    fun settings() = capture { RangeSettingsScreen(RangeAppearance(), onAppearanceChange = {}, onBack = {}, onOpenAbout = {}) }

    @Test
    fun about() = capture {
        RangeAboutScreen(
            appName = "Range Tasks",
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
    private fun capture(appearance: RangeAppearance = RangeAppearance(dynamicColor = false), content: @Composable () -> Unit) {
        compose.setContent { RangeTheme(accent = RangeAccents.Tasks, appearance = appearance, content = content) }
        compose.waitForIdle()
        captureScreenRoboImage()
    }
}

private const val PHONE = "w412dp-h915dp-night-xxhdpi"
