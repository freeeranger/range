package dev.freeranger.range.tasks.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.freeranger.range.tasks.LocalAppContainer
import dev.freeranger.range.tasks.R
import dev.freeranger.range.tasks.data.Backup
import dev.freeranger.range.tasks.data.Task
import dev.freeranger.range.tasks.ui.common.rememberTaskFormat
import dev.freeranger.range.ui.components.RangeSectionHeader
import dev.freeranger.range.ui.components.RangeTimePickerDialog
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.settings.RangeAppearance
import dev.freeranger.range.ui.settings.RangeBackup
import dev.freeranger.range.ui.settings.RangeSettingRow
import dev.freeranger.range.ui.settings.RangeSettingsScreen
import kotlinx.coroutines.launch
import java.time.LocalTime

/** The shared settings, with Tasks' on top: when tasks without a time remind, and backup. */
@Composable
fun TasksSettingsRoute(onBack: () -> Unit, onOpenAbout: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val appearance by container.appearance.appearance.collectAsStateWithLifecycle(RangeAppearance())
    val allDayReminder by container.preferences.allDayReminder.collectAsStateWithLifecycle(Task.ALL_DAY_REMINDER)
    val appName = stringResource(R.string.app_title)
    val backup = remember(container, appName) {
        RangeBackup(
            appName = appName,
            filePrefix = "tasks",
            scope = container.scope,
            export = container.repository::exportBackup,
            isBackup = { Backup.decode(it) != null },
            restore = container.repository::importBackup,
        )
    }

    TasksSettingsScreen(
        appearance = appearance,
        onAppearanceChange = { scope.launch { container.appearance.set(it) } },
        allDayReminder = allDayReminder,
        onAllDayReminderChange = {
            container.scope.launch {
                container.preferences.setAllDayReminder(it)
                // Alarms already set are for the old time.
                container.repository.syncAllReminders()
            }
        },
        onBack = onBack,
        onOpenAbout = onOpenAbout,
        backup = backup,
    )
}

/** The shared settings with Tasks' own first: reminders, then Backup. */
@Composable
fun TasksSettingsScreen(
    appearance: RangeAppearance,
    onAppearanceChange: (RangeAppearance) -> Unit,
    allDayReminder: LocalTime,
    onAllDayReminderChange: (LocalTime) -> Unit,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    backup: RangeBackup?,
    initiallyPickingTime: Boolean = false,
) {
    val format = rememberTaskFormat()
    var pickingTime by rememberSaveable { mutableStateOf(initiallyPickingTime) }
    RangeSettingsScreen(
        appearance = appearance,
        onAppearanceChange = onAppearanceChange,
        onBack = onBack,
        onOpenAbout = onOpenAbout,
        backup = backup,
        content = {
            item { RangeSectionHeader(stringResource(R.string.settings_reminders)) }
            item {
                RangeSettingRow(
                    RangeIcons.Schedule,
                    stringResource(R.string.settings_all_day_reminder),
                    value = format.time(allDayReminder),
                    onClick = { pickingTime = true },
                )
            }
        },
    )

    if (pickingTime) {
        RangeTimePickerDialog(
            initial = allDayReminder,
            title = stringResource(R.string.settings_all_day_reminder),
            confirmText = stringResource(R.string.dialog_ok),
            dismissText = stringResource(R.string.dialog_cancel),
            onConfirm = {
                pickingTime = false
                onAllDayReminderChange(it)
            },
            onDismiss = { pickingTime = false },
        )
    }
}
