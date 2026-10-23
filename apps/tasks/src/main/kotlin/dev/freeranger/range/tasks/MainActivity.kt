package dev.freeranger.range.tasks

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.freeranger.range.tasks.reminders.ReminderReceiver
import dev.freeranger.range.ui.theme.RangeAccents
import dev.freeranger.range.ui.theme.RangeTheme
import dev.freeranger.range.ui.theme.isDark
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    /** A task to open, set when the app is launched from a reminder notification. */
    private var taskToOpen by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) taskToOpen = intent.taskId()

        val container = (application as TasksApplication).container
        // A few small preferences: read them before the first frame so a chosen theme never flashes the wrong one.
        val initialAppearance = runBlocking { container.appearance.appearance.first() }

        setContent {
            val appearance by container.appearance.appearance.collectAsStateWithLifecycle(initialAppearance)
            val dark = appearance.isDark()
            DisposableEffect(dark) {
                // Transparent bars with icons that contrast with the app's theme, not the system's.
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            RangeTheme(accent = RangeAccents.Tasks, appearance = appearance) {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    TasksApp(taskToOpen = taskToOpen, onTaskOpened = { taskToOpen = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.taskId()?.let { taskToOpen = it }
    }

    private fun Intent.taskId(): Long? = getLongExtra(ReminderReceiver.EXTRA_TASK_ID, -1).takeIf { it >= 0 }
}
