package dev.freeranger.range.notes.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.freeranger.range.notes.LocalAppContainer
import dev.freeranger.range.notes.R
import dev.freeranger.range.notes.data.Backup
import dev.freeranger.range.notes.data.NoteLock
import dev.freeranger.range.ui.settings.RangeAppearance
import dev.freeranger.range.ui.settings.RangeBackup
import dev.freeranger.range.ui.settings.RangeSettingsScreen
import kotlinx.coroutines.launch

/** The shared settings with Backup, which unlocks locked notes first: Notes has no settings of its own yet. */
@Composable
fun NotesSettingsRoute(onBack: () -> Unit, onOpenAbout: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val appearance by container.appearance.appearance.collectAsStateWithLifecycle(RangeAppearance())
    val activity = LocalActivity.current
    val appName = stringResource(R.string.app_title)
    val unlockTitle = stringResource(R.string.unlock_title)
    val lock = container.lock
    // Locked notes go into a backup, and come back out of one, only once they're unlocked.
    val unlockThen = { proceed: () -> Unit ->
        if (activity == null || lock.isUnlocked || !lock.canLock) {
            proceed()
        } else {
            lock.unlock(activity, unlockTitle) { if (it == NoteLock.Result.Unlocked) proceed() }
        }
    }
    val backup = remember(container, appName, activity) {
        RangeBackup(
            appName = appName,
            filePrefix = "notes",
            scope = container.scope,
            export = container.repository::exportBackup,
            isBackup = { Backup.decode(it) != null },
            restore = container.repository::importBackup,
            beforeExport = { proceed ->
                scope.launch {
                    val pick = {
                        // Picking where to save leaves the app for a moment; that shouldn't lock the notes being saved.
                        lock.keepUnlockedThroughNextLeave()
                        proceed()
                    }
                    if (container.repository.anyLocked()) unlockThen(pick) else proceed()
                }
            },
            beforeRestore = { text, proceed ->
                if (Backup.decode(text)?.notes.orEmpty().any { it.locked }) unlockThen(proceed) else proceed()
            },
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
