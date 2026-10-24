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
import dev.freeranger.range.lift.data.PickTarget
import dev.freeranger.range.lift.ui.exercise.ExerciseDetailRoute
import dev.freeranger.range.lift.ui.exercise.ExerciseEditRoute
import dev.freeranger.range.lift.ui.exercise.ExercisePickerRoute
import dev.freeranger.range.lift.ui.home.HomeRoute
import dev.freeranger.range.lift.ui.settings.LiftSettingsRoute
import dev.freeranger.range.lift.ui.template.TemplateRoute
import dev.freeranger.range.lift.ui.template.TemplatesRoute
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

/** Picking exercises for [target]: to add to a workout or template, or to replace one of its exercises. */
@Serializable
data class ExercisePickerKey(val target: PickTarget) : NavKey

/** Every template; [creating] opens onto naming a new one. */
@Serializable
data class TemplatesKey(val creating: Boolean = false) : NavKey

/** Template [id], opened straight into [editing] it; [isNew] if it was just made. */
@Serializable
data class TemplateKey(val id: Long, val editing: Boolean = false, val isNew: Boolean = false) : NavKey

@Serializable
data class ExerciseKey(val id: Long) : NavKey

/** Editing exercise [id], or adding a new one when it's null: straight to [target], if it was being picked. */
@Serializable
data class ExerciseEditKey(val id: Long? = null, val target: PickTarget? = null) : NavKey

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

            val openWorkout: (Long) -> Unit = { backStack.add(WorkoutKey(it)) }

            entry<HomeKey> {
                HomeRoute(
                    onOpenWorkout = openWorkout,
                    onOpenTemplates = { backStack.add(TemplatesKey(creating = it)) },
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
                    onAddExercise = { backStack.add(ExercisePickerKey(PickTarget.IntoWorkout(key.id))) },
                    onSwapExercise = { backStack.add(ExercisePickerKey(PickTarget.SwapInWorkout(it))) },
                    onOpenExercise = openExercise,
                    onRepeated = {
                        back()
                        openWorkout(it)
                    },
                )
            }
            entry<TemplatesKey> { key ->
                TemplatesRoute(
                    creating = key.creating,
                    onBack = back,
                    onOpen = { backStack.add(TemplateKey(it)) },
                    onCreated = { backStack.add(TemplateKey(it, editing = true, isNew = true)) },
                )
            }
            entry<TemplateKey> { key ->
                TemplateRoute(
                    id = key.id,
                    editing = key.editing,
                    isNew = key.isNew,
                    onBack = back,
                    onStarted = { workoutId ->
                        // The workout takes the templates' place, so finishing it goes home.
                        while (backStack.lastOrNull().let { it is TemplateKey || it is TemplatesKey }) back()
                        openWorkout(workoutId)
                    },
                    onAddExercise = { backStack.add(ExercisePickerKey(PickTarget.IntoTemplate(key.id))) },
                    onSwapExercise = { backStack.add(ExercisePickerKey(PickTarget.SwapInTemplate(it))) },
                    onOpenExercise = openExercise,
                )
            }
            entry<SummaryKey> { key ->
                SummaryRoute(id = key.id, onDone = back)
            }
            entry<ExercisePickerKey> { key ->
                ExercisePickerRoute(
                    target = key.target,
                    onDone = back,
                    onCreate = { backStack.add(ExerciseEditKey(target = key.target)) },
                )
            }
            entry<ExerciseKey> { key ->
                ExerciseDetailRoute(id = key.id, onBack = back, onEdit = { backStack.add(ExerciseEditKey(id = key.id)) })
            }
            entry<ExerciseEditKey> { key ->
                ExerciseEditRoute(
                    id = key.id,
                    target = key.target,
                    onSaved = {
                        back()
                        // A new exercise went straight where it was being picked for, so the picker is done too.
                        if (key.target != null && backStack.lastOrNull() is ExercisePickerKey) back()
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
