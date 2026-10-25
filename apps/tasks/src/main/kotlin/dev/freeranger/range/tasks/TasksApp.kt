package dev.freeranger.range.tasks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.freeranger.range.tasks.data.Task
import dev.freeranger.range.tasks.ui.detail.TaskDetailRoute
import dev.freeranger.range.tasks.ui.list.TaskListRoute
import dev.freeranger.range.tasks.ui.settings.TasksSettingsRoute
import dev.freeranger.range.ui.settings.RangeAboutScreen
import dev.freeranger.range.ui.settings.RangeComponentsScreen
import dev.freeranger.range.ui.settings.RangeLicense
import dev.freeranger.range.ui.settings.RangeLicenseScreen
import dev.freeranger.range.ui.theme.RangeTransitions
import kotlinx.serialization.Serializable

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("No AppContainer provided") }

/** When tasks without a time remind, from Settings. */
val LocalAllDayReminder = compositionLocalOf { Task.ALL_DAY_REMINDER }

@Serializable
data object TaskListKey : NavKey

@Serializable
data class TaskDetailKey(val id: Long) : NavKey

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
fun TasksApp(taskToOpen: Long?, onTaskOpened: () -> Unit) {
    val backStack = rememberNavBackStack(TaskListKey)
    val container = LocalAppContainer.current
    val allDayReminder by container.preferences.allDayReminder.collectAsStateWithLifecycle(Task.ALL_DAY_REMINDER)
    // A task deleted from its page, handed to the list so it can offer Undo.
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(taskToOpen) {
        if (taskToOpen == null) return@LaunchedEffect
        // Opening from a notification: show that task, with the list behind it for Back.
        backStack.retainAll { it == TaskListKey }
        backStack.add(TaskDetailKey(taskToOpen))
        onTaskOpened()
    }

    CompositionLocalProvider(LocalAllDayReminder provides allDayReminder) {
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

                entry<TaskListKey> {
                    TaskListRoute(
                        onOpenTask = { backStack.add(TaskDetailKey(it)) },
                        onOpenSettings = { backStack.add(SettingsKey) },
                        deleting = deleting,
                        onDeleted = { deleting = null },
                    )
                }
                entry<TaskDetailKey> { key ->
                    TaskDetailRoute(
                        id = key.id,
                        onBack = back,
                        onDelete = {
                            deleting = key.id
                            back()
                        },
                    )
                }
                entry<SettingsKey> {
                    TasksSettingsRoute(onBack = back, onOpenAbout = { backStack.add(AboutKey) })
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
}
