package dev.freeranger.range.lift

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.freeranger.range.lift.ui.exercise.ExerciseDetailRoute
import dev.freeranger.range.lift.ui.exercise.ExerciseEditRoute
import dev.freeranger.range.lift.ui.exercise.ExercisePickerRoute
import dev.freeranger.range.lift.ui.home.HomeRoute
import dev.freeranger.range.lift.ui.settings.LiftSettingsRoute
import dev.freeranger.range.lift.ui.workout.SummaryRoute
import dev.freeranger.range.lift.ui.workout.WorkoutRoute
import dev.freeranger.range.ui.settings.RangeAboutScreen
import dev.freeranger.range.ui.settings.RangeComponent
import dev.freeranger.range.ui.settings.RangeComponents
import dev.freeranger.range.ui.settings.RangeComponentsScreen
import dev.freeranger.range.ui.settings.RangeLicense
import dev.freeranger.range.ui.settings.RangeLicenseScreen
import dev.freeranger.range.ui.theme.RangeTransitions
import kotlinx.serialization.Serializable

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("No AppContainer provided") }

@Serializable
data object HomeKey : NavKey

@Serializable
data class WorkoutKey(val id: Long) : NavKey

/** Right after finishing workout [id]. */
@Serializable
data class SummaryKey(val id: Long) : NavKey

/** Picking an exercise to add to the workout [workoutId]. */
@Serializable
data class ExercisePickerKey(val workoutId: Long) : NavKey

@Serializable
data class ExerciseKey(val id: Long) : NavKey

/** Editing exercise [id], or adding a new one when it's null: straight into workout [workoutId], if given. */
@Serializable
data class ExerciseEditKey(val id: Long? = null, val workoutId: Long? = null) : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data object AboutKey : NavKey

@Serializable
data object ComponentsKey : NavKey

/** [license] is a [RangeLicense] name; keys must be serializable and the enum lives in range-ui. */
@Serializable
data class LicenseKey(val license: String) : NavKey

/** [openWorkout] is a workout to show, from the rest timer's notification; [onOpened] says it's been shown. */
@Composable
fun LiftApp(openWorkout: Long? = null, onOpened: () -> Unit = {}) {
    val backStack = rememberNavBackStack(HomeKey)
    LaunchedEffect(openWorkout) {
        if (openWorkout == null) return@LaunchedEffect
        if (backStack.lastOrNull() != WorkoutKey(openWorkout)) backStack.add(WorkoutKey(openWorkout))
        onOpened()
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = { RangeTransitions.forward() },
        popTransitionSpec = { RangeTransitions.back() },
        predictivePopTransitionSpec = { RangeTransitions.predictiveBack() },
        entryProvider = entryProvider {
            val back: () -> Unit = { backStack.removeLastOrNull() }
            val openLicense: (RangeLicense) -> Unit = { backStack.add(LicenseKey(it.name)) }
            val openExercise: (Long) -> Unit = { backStack.add(ExerciseKey(it)) }

            entry<HomeKey> {
                HomeRoute(
                    onOpenWorkout = { backStack.add(WorkoutKey(it)) },
                    onOpenExercise = openExercise,
                    onNewExercise = { backStack.add(ExerciseEditKey()) },
                    onOpenSettings = { backStack.add(SettingsKey) },
                )
            }
            entry<WorkoutKey> { key ->
                WorkoutRoute(
                    id = key.id,
                    onBack = back,
                    onFinished = {
                        back()
                        backStack.add(SummaryKey(key.id))
                    },
                    onAddExercise = { backStack.add(ExercisePickerKey(key.id)) },
                    onOpenExercise = openExercise,
                )
            }
            entry<SummaryKey> { key ->
                SummaryRoute(id = key.id, onDone = back)
            }
            entry<ExercisePickerKey> { key ->
                ExercisePickerRoute(
                    workoutId = key.workoutId,
                    onDone = back,
                    onCreate = { backStack.add(ExerciseEditKey(workoutId = key.workoutId)) },
                )
            }
            entry<ExerciseKey> { key ->
                ExerciseDetailRoute(id = key.id, onBack = back, onEdit = { backStack.add(ExerciseEditKey(id = key.id)) })
            }
            entry<ExerciseEditKey> { key ->
                ExerciseEditRoute(
                    id = key.id,
                    workoutId = key.workoutId,
                    onSaved = {
                        back()
                        // A new exercise went straight into the workout, so the picker is done too.
                        if (key.workoutId != null && backStack.lastOrNull() is ExercisePickerKey) back()
                    },
                    onCancel = back,
                )
            }
            entry<SettingsKey> {
                LiftSettingsRoute(onBack = back, onOpenAbout = { backStack.add(AboutKey) })
            }
            entry<AboutKey> {
                RangeAboutScreen(
                    appName = stringResource(R.string.app_name),
                    iconBackground = R.drawable.ic_launcher_background,
                    iconForeground = R.drawable.ic_launcher_foreground,
                    onBack = back,
                    onOpenLicense = openLicense,
                    onOpenComponents = { backStack.add(ComponentsKey) },
                )
            }
            entry<ComponentsKey> {
                RangeComponentsScreen(
                    onBack = back,
                    onOpenLicense = openLicense,
                    components = RangeComponents + RangeComponent("Reorderable", "Calvin Liang", RangeLicense.Apache2),
                )
            }
            entry<LicenseKey> { key ->
                RangeLicenseScreen(license = RangeLicense.valueOf(key.license), onBack = back)
            }
        },
    )
}
