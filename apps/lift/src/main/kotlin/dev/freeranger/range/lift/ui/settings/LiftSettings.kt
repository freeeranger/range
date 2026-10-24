package dev.freeranger.range.lift.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.freeranger.range.lift.LocalAppContainer
import dev.freeranger.range.lift.R
import dev.freeranger.range.lift.data.Backup
import dev.freeranger.range.lift.ui.common.rememberNotificationPermissionRequest
import dev.freeranger.range.ui.components.RangeSectionHeader
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.settings.RangeAppearance
import dev.freeranger.range.ui.settings.RangeSettingRow
import dev.freeranger.range.ui.settings.RangeSettingsScreen
import dev.freeranger.range.ui.settings.RangeSwitchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * The shared settings, with Lift's on top: keeping the screen on and a notification during workouts, and backup,
 * everything saved to a file the user picks and restored from one.
 */
@Composable
fun LiftSettingsRoute(onBack: () -> Unit, onOpenAbout: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appearance by container.appearance.appearance.collectAsStateWithLifecycle(RangeAppearance())
    val keepScreenOn by container.preferences.keepScreenOn.collectAsStateWithLifecycle(false)
    val workoutNotification by container.preferences.workoutNotification.collectAsStateWithLifecycle(false)
    val requestNotifications = rememberNotificationPermissionRequest()
    // A backup file read and waiting for the go-ahead to replace everything.
    var restoring by remember { mutableStateOf<String?>(null) }
    var invalid by remember { mutableStateOf(false) }
    val saved = stringResource(R.string.backup_saved)
    val restored = stringResource(R.string.backup_restored)

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        container.scope.launch {
            val text = container.repository.exportBackup()
            val written = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } }.isSuccess
            }
            if (written) withContext(Dispatchers.Main) { Toast.makeText(context, saved, Toast.LENGTH_SHORT).show() }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            if (text != null && Backup.decode(text) != null) restoring = text else invalid = true
        }
    }

    LiftSettingsScreen(
        appearance = appearance,
        onAppearanceChange = { scope.launch { container.appearance.set(it) } },
        keepScreenOn = keepScreenOn,
        onKeepScreenOnChange = { scope.launch { container.preferences.setKeepScreenOn(it) } },
        workoutNotification = workoutNotification,
        onWorkoutNotificationChange = {
            if (it) requestNotifications()
            scope.launch { container.preferences.setWorkoutNotification(it) }
        },
        onBack = onBack,
        onOpenAbout = onOpenAbout,
        onExport = { export.launch("lift-${LocalDate.now()}.json") },
        onRestore = { import.launch(arrayOf("*/*")) },
    )

    restoring?.let { text ->
        AlertDialog(
            onDismissRequest = { restoring = null },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        restoring = null
                        container.scope.launch {
                            val ok = container.repository.importBackup(text)
                            withContext(Dispatchers.Main) {
                                if (ok) Toast.makeText(context, restored, Toast.LENGTH_SHORT).show() else invalid = true
                            }
                        }
                    },
                ) { Text(stringResource(R.string.backup_restore_confirm)) }
            },
            dismissButton = { TextButton(onClick = { restoring = null }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
    if (invalid) {
        AlertDialog(
            onDismissRequest = { invalid = false },
            title = { Text(stringResource(R.string.backup_invalid)) },
            confirmButton = { TextButton(onClick = { invalid = false }) { Text(stringResource(R.string.dialog_ok)) } },
        )
    }
}

/** The shared settings with Lift's own first: the workout screen, then Backup. */
@Composable
fun LiftSettingsScreen(
    appearance: RangeAppearance,
    onAppearanceChange: (RangeAppearance) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    workoutNotification: Boolean,
    onWorkoutNotificationChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    onExport: () -> Unit,
    onRestore: () -> Unit,
) {
    RangeSettingsScreen(
        appearance = appearance,
        onAppearanceChange = onAppearanceChange,
        onBack = onBack,
        onOpenAbout = onOpenAbout,
        content = {
            item { RangeSectionHeader(stringResource(R.string.settings_workout)) }
            item { RangeSwitchRow(RangeIcons.LightMode, stringResource(R.string.settings_keep_screen_on), keepScreenOn, onKeepScreenOnChange) }
            item {
                RangeSwitchRow(RangeIcons.Notifications, stringResource(R.string.settings_workout_notification), workoutNotification, onWorkoutNotificationChange)
            }
            item { RangeSectionHeader(stringResource(R.string.settings_backup)) }
            item { RangeSettingRow(RangeIcons.Save, stringResource(R.string.backup_export), onClick = onExport) }
            item { RangeSettingRow(RangeIcons.SettingsBackupRestore, stringResource(R.string.backup_restore), onClick = onRestore) }
        },
    )
}
