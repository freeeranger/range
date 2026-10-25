package dev.freeranger.range.tasks

import android.app.Application
import android.content.Context
import dev.freeranger.range.tasks.data.TaskRepository
import dev.freeranger.range.tasks.data.TasksDatabase
import dev.freeranger.range.tasks.data.TasksPreferences
import dev.freeranger.range.tasks.reminders.AlarmReminders
import dev.freeranger.range.tasks.reminders.TaskNotifications
import dev.freeranger.range.ui.settings.RangeAppearanceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Everything that lives as long as the app. Built by hand: the app is small enough not to need a DI framework. */
class AppContainer(context: Context) {
    /** For work that must outlive a screen: saving on exit, handling notification buttons. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val appearance = RangeAppearanceStore(context)
    val preferences = TasksPreferences(context)
    val repository = TaskRepository(
        dao = TasksDatabase.create(context).tasks(),
        reminders = AlarmReminders(context, allDayAt = { preferences.allDayReminder.first() }),
    )
}

class TasksApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        TaskNotifications.createChannel(this)
        // Cheap, and repairs any alarm the system dropped while the app wasn't running.
        container.scope.launch { container.repository.syncAllReminders() }
    }
}
