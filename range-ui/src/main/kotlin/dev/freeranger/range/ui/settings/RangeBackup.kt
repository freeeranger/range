package dev.freeranger.range.ui.settings

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
import dev.freeranger.range.ui.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Everything an app keeps, saved to a file the user picks and restored from one. The app says how
 * its data becomes text and back; the settings screen does the files, the go-ahead and the toasts.
 */
class RangeBackup(
    /** The app's short name, as in "Replace everything in Tasks with this backup?". */
    val appName: String,
    /** Starts the suggested file name, followed by the date: "tasks" makes "tasks-2026-10-25.json". */
    val filePrefix: String,
    /** Outlives the settings screen, so a backup being written or restored isn't cut short by leaving. */
    val scope: CoroutineScope,
    val export: suspend () -> String,
    /** Whether a file's text is a backup this version can read, before asking to replace everything. */
    val isBackup: (String) -> Boolean,
    /** Replaces everything with the backup. False, and nothing changed, if it couldn't. */
    val restore: suspend (String) -> Boolean,
)

/** Starts saving or restoring [RangeBackup]s, and shows what comes of it. */
internal class BackupActions(val export: () -> Unit, val restore: () -> Unit)

/** The file pickers and dialogs for [backup]; a no-op when the app has no backup. */
@Composable
internal fun rememberBackupActions(backup: RangeBackup?): BackupActions {
    val context = LocalContext.current
    val screenScope = rememberCoroutineScope()
    // A backup file read and waiting for the go-ahead to replace everything.
    var restoring by remember { mutableStateOf<String?>(null) }
    var invalid by remember { mutableStateOf(false) }
    val saved = stringResource(R.string.range_backup_saved)
    val restored = stringResource(R.string.range_backup_restored)

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null || backup == null) return@rememberLauncherForActivityResult
        backup.scope.launch {
            val text = backup.export()
            val written = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } }.isSuccess
            }
            if (written) withContext(Dispatchers.Main) { Toast.makeText(context, saved, Toast.LENGTH_SHORT).show() }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || backup == null) return@rememberLauncherForActivityResult
        screenScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            if (text != null && backup.isBackup(text)) restoring = text else invalid = true
        }
    }

    if (backup != null) {
        restoring?.let { text ->
            AlertDialog(
                onDismissRequest = { restoring = null },
                title = { Text(stringResource(R.string.range_backup_restore_title, backup.appName)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            restoring = null
                            backup.scope.launch {
                                val ok = backup.restore(text)
                                withContext(Dispatchers.Main) {
                                    if (ok) Toast.makeText(context, restored, Toast.LENGTH_SHORT).show() else invalid = true
                                }
                            }
                        },
                    ) { Text(stringResource(R.string.range_backup_restore_confirm)) }
                },
                dismissButton = { TextButton(onClick = { restoring = null }) { Text(stringResource(R.string.range_cancel)) } },
            )
        }
        if (invalid) {
            AlertDialog(
                onDismissRequest = { invalid = false },
                title = { Text(stringResource(R.string.range_backup_invalid, backup.appName)) },
                confirmButton = { TextButton(onClick = { invalid = false }) { Text(stringResource(R.string.range_ok)) } },
            )
        }
    }

    return BackupActions(
        export = { if (backup != null) export.launch("${backup.filePrefix}-${LocalDate.now()}.json") },
        restore = { import.launch(arrayOf("*/*")) },
    )
}
