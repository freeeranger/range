package dev.freeranger.range.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.freeranger.range.notes.ui.list.NoteListRoute
import dev.freeranger.range.notes.ui.note.ImageScreen
import dev.freeranger.range.notes.ui.note.NoteRoute
import dev.freeranger.range.notes.ui.settings.NotesSettingsRoute
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
data object NoteListKey : NavKey

/** Note [id], [isNew] if it was just started, so the keyboard comes up. */
@Serializable
data class NoteKey(val id: Long, val isNew: Boolean = false) : NavKey

/** One of a note's pictures, [name] being its file. */
@Serializable
data class ImageKey(val name: String) : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data object AboutKey : NavKey

@Serializable
data object ComponentsKey : NavKey

/** [license] is a [RangeLicense] name; keys must be serializable and the enum lives in range-ui. */
@Serializable
data class LicenseKey(val license: String) : NavKey

/** [opening] is a note to show, just made from a shortcut or something shared; [onOpened] says it's been shown. */
@Composable
fun NotesApp(opening: NoteKey? = null, onOpened: () -> Unit = {}) {
    val backStack = rememberNavBackStack(NoteListKey)
    LaunchedEffect(opening) {
        if (opening == null) return@LaunchedEffect
        // The new note, with the list behind it for Back.
        backStack.retainAll { it == NoteListKey }
        backStack.add(opening)
        onOpened()
    }
    // A note deleted from its page, handed to the list so it can offer Undo.
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }

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

            entry<NoteListKey> {
                NoteListRoute(
                    onOpen = { id, isNew -> backStack.add(NoteKey(id, isNew)) },
                    onOpenSettings = { backStack.add(SettingsKey) },
                    deleting = deleting,
                    onDeleted = { deleting = null },
                )
            }
            entry<NoteKey> { key ->
                NoteRoute(
                    id = key.id,
                    isNew = key.isNew,
                    onBack = back,
                    onDelete = {
                        deleting = key.id
                        back()
                    },
                    onOpenImage = { backStack.add(ImageKey(it)) },
                )
            }
            entry<ImageKey> { key -> ImageScreen(key.name, onBack = back) }
            entry<SettingsKey> {
                NotesSettingsRoute(onBack = back, onOpenAbout = { backStack.add(AboutKey) })
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
