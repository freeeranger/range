package dev.freeranger.range.tasks.ui.common

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** The current time, updated on each minute, so "overdue" and "Today" stay right while the app is open. */
@Composable
fun rememberNow(): LocalDateTime {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            val nextMinute = value.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
            delay(Duration.between(value, nextMinute).toMillis() + 50)
        }
    }
    return now
}

/**
 * Returns a function that asks for notification permission if the app doesn't have it yet.
 * Called when a task first gets a date, so the request comes with an obvious reason.
 */
@Composable
fun rememberNotificationPermissionRequest(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return remember(context, launcher) {
        {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
