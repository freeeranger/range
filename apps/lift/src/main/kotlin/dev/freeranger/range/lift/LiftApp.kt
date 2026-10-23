package dev.freeranger.range.lift

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.freeranger.range.lift.ui.exercise.ExerciseDetailRoute
import dev.freeranger.range.lift.ui.exercise.ExercisePickerRoute
import dev.freeranger.range.lift.ui.home.HomeRoute
import dev.freeranger.range.lift.ui.workout.WorkoutRoute
import dev.freeranger.range.ui.settings.RangeAboutScreen
import dev.freeranger.range.ui.settings.RangeAppearance
import dev.freeranger.range.ui.settings.RangeComponentsScreen
import dev.freeranger.range.ui.settings.RangeLicense
import dev.freeranger.range.ui.settings.RangeLicenseScreen
import dev.freeranger.range.ui.settings.RangeSettingsScreen
import dev.freeranger.range.ui.theme.RangeTransitions
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("No AppContainer provided") }

@Serializable
data object HomeKey : NavKey

@Serializable
data class WorkoutKey(val id: Long) : NavKey

/** Picking an exercise to add to the workout [workoutId]. */
@Serializable
data class ExercisePickerKey(val workoutId: Long) : NavKey

@Serializable
data class ExerciseKey(val id: Long) : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data object AboutKey : NavKey

@Serializable
data object ComponentsKey : NavKey

/** [license] is a [RangeLicense] name; keys must be serializable and the enum lives in range-ui. */
@Serializable
data class LicenseKey(val license: String) : NavKey

@Composable
fun LiftApp() {
    val backStack = rememberNavBackStack(HomeKey)

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
                    onOpenSettings = { backStack.add(SettingsKey) },
                )
            }
            entry<WorkoutKey> { key ->
                WorkoutRoute(
                    id = key.id,
                    onBack = back,
                    onAddExercise = { backStack.add(ExercisePickerKey(key.id)) },
                    onOpenExercise = openExercise,
                )
            }
            entry<ExercisePickerKey> { key ->
                ExercisePickerRoute(workoutId = key.workoutId, onDone = back)
            }
            entry<ExerciseKey> { key ->
                ExerciseDetailRoute(id = key.id, onBack = back)
            }
            entry<SettingsKey> {
                val store = LocalAppContainer.current.appearance
                val appearance by store.appearance.collectAsStateWithLifecycle(RangeAppearance())
                val scope = rememberCoroutineScope()
                RangeSettingsScreen(
                    appearance = appearance,
                    onAppearanceChange = { scope.launch { store.set(it) } },
                    onBack = back,
                    onOpenAbout = { backStack.add(AboutKey) },
                )
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
                RangeComponentsScreen(onBack = back, onOpenLicense = openLicense)
            }
            entry<LicenseKey> { key ->
                RangeLicenseScreen(license = RangeLicense.valueOf(key.license), onBack = back)
            }
        },
    )
}
