package dev.freeranger.range.tasks.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.tasksSettings by preferencesDataStore(name = "tasks_settings")

/** Tasks' own settings, saved on the phone. */
class TasksPreferences(context: Context) {
    private val store = context.applicationContext.tasksSettings

    /** When a task with a day but no time reminds: [Task.ALL_DAY_REMINDER] unless the user picks another. */
    val allDayReminder: Flow<LocalTime> = store.data.map { prefs ->
        prefs[ALL_DAY_REMINDER]?.let { LocalTime.ofSecondOfDay(it * 60L) } ?: Task.ALL_DAY_REMINDER
    }

    suspend fun setAllDayReminder(time: LocalTime) {
        store.edit { it[ALL_DAY_REMINDER] = time.hour * 60 + time.minute }
    }

    private companion object {
        /** Minutes after midnight. */
        val ALL_DAY_REMINDER = intPreferencesKey("all_day_reminder")
    }
}
