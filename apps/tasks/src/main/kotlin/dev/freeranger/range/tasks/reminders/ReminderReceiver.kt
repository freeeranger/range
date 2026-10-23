package dev.freeranger.range.tasks.reminders

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.freeranger.range.tasks.TasksApplication
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** Shows a task's notification when its alarm fires, and handles the notification's Complete button. */
class ReminderReceiver : BroadcastReceiver() {
    /** The day and time (null for all-day tasks) a reminder is for. */
    data class Moment(val date: LocalDate, val time: LocalTime?)

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
        val moment = intent.moment()
        if (taskId < 0 || moment == null) return
        val container = (context.applicationContext as TasksApplication).container
        val pending = goAsync()
        container.scope.launch {
            try {
                when (intent.action) {
                    ACTION_REMIND -> {
                        // Skip it if the task was ticked off or moved since the alarm was set.
                        container.repository.get(taskId)
                            ?.takeIf { it.dueDate == moment.date && it.wantsReminder(moment.time) }
                            ?.let { TaskNotifications.show(context, it, moment) }
                        container.repository.syncReminder(taskId)
                    }
                    ACTION_COMPLETE -> container.repository.completeFromReminder(taskId, moment.date)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "dev.freeranger.range.tasks.REMIND"
        const val ACTION_COMPLETE = "dev.freeranger.range.tasks.COMPLETE"
        const val EXTRA_TASK_ID = "taskId"
        private const val EXTRA_DATE = "date"
        private const val EXTRA_TIME = "time"

        fun intent(context: Context, action: String, taskId: Long, moment: Moment?): Intent =
            Intent(context, ReminderReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_TASK_ID, taskId)
                .apply {
                    if (moment != null) {
                        putExtra(EXTRA_DATE, moment.date.toEpochDay())
                        putExtra(EXTRA_TIME, moment.time?.toSecondOfDay() ?: -1)
                    }
                }

        private fun Intent.moment(): Moment? {
            if (!hasExtra(EXTRA_DATE)) return null
            val time = getIntExtra(EXTRA_TIME, -1).takeIf { it >= 0 }?.let { LocalTime.ofSecondOfDay(it.toLong()) }
            return Moment(LocalDate.ofEpochDay(getLongExtra(EXTRA_DATE, 0)), time)
        }
    }
}

/** Puts every alarm back after a reboot, an app update, or a clock or time zone change. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        val container = (context.applicationContext as TasksApplication).container
        val pending = goAsync()
        container.scope.launch {
            try {
                container.repository.syncAllReminders()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
