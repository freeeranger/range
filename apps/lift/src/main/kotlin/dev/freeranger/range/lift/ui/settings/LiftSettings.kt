package dev.freeranger.range.lift.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.freeranger.range.lift.LocalAppContainer
import dev.freeranger.range.lift.R
import dev.freeranger.range.lift.data.Backup
import dev.freeranger.range.lift.ui.common.rememberNotificationPermissionRequest
import dev.freeranger.range.ui.components.RangeSectionHeader
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.settings.RangeAppearance
import dev.freeranger.range.ui.settings.RangeBackup
import dev.freeranger.range.ui.settings.RangeSettingsScreen
import dev.freeranger.range.ui.settings.RangeSwitchRow
import kotlinx.coroutines.launch

/** The shared settings, with Lift's on top: keeping the screen on and a notification during workouts, and backup. */
@Composable
fun LiftSettingsRoute(onBack: () -> Unit, onOpenAbout: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val appearance by container.appearance.appearance.collectAsStateWithLifecycle(RangeAppearance())
    val keepScreenOn by container.preferences.keepScreenOn.collectAsStateWithLifecycle(false)
    val workoutNotification by container.preferences.workoutNotification.collectAsStateWithLifecycle(false)
    val requestNotifications = rememberNotificationPermissionRequest()
    val appName = stringResource(R.string.app_title)
    val backup = remember(container, appName) {
        RangeBackup(
            appName = appName,
            filePrefix = "lift",
            scope = container.scope,
            export = container.repository::exportBackup,
            isBackup = { Backup.decode(it) != null },
            restore = container.repository::importBackup,
        )
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
        backup = backup,
    )
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
    backup: RangeBackup?,
) {
    RangeSettingsScreen(
        appearance = appearance,
        onAppearanceChange = onAppearanceChange,
        onBack = onBack,
        onOpenAbout = onOpenAbout,
        backup = backup,
        content = {
            item { RangeSectionHeader(stringResource(R.string.settings_workout)) }
            item { RangeSwitchRow(RangeIcons.LightMode, stringResource(R.string.settings_keep_screen_on), keepScreenOn, onKeepScreenOnChange) }
            item {
                RangeSwitchRow(RangeIcons.Notifications, stringResource(R.string.settings_workout_notification), workoutNotification, onWorkoutNotificationChange)
            }
        },
    )
}
