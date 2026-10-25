package dev.freeranger.range.notes.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.freeranger.range.notes.LocalAppContainer
import dev.freeranger.range.notes.R
import dev.freeranger.range.notes.data.Backup
import dev.freeranger.range.ui.settings.RangeAppearance
import dev.freeranger.range.ui.settings.RangeBackup
import dev.freeranger.range.ui.settings.RangeSettingsScreen
import kotlinx.coroutines.launch

/** The shared settings with Backup: Notes has none of its own yet. */
@Composable
fun NotesSettingsRoute(onBack: () -> Unit, onOpenAbout: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val appearance by container.appearance.appearance.collectAsStateWithLifecycle(RangeAppearance())
    val appName = stringResource(R.string.app_title)
    val backup = remember(container, appName) {
        RangeBackup(
            appName = appName,
            filePrefix = "notes",
            scope = container.scope,
            export = container.repository::exportBackup,
            isBackup = { Backup.decode(it) != null },
            restore = container.repository::importBackup,
        )
    }
    RangeSettingsScreen(
        appearance = appearance,
        onAppearanceChange = { scope.launch { container.appearance.set(it) } },
        onBack = onBack,
        onOpenAbout = onOpenAbout,
        backup = backup,
    )
}
