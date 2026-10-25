package dev.freeranger.range.tasks.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.freeranger.range.tasks.data.Reminders
import dev.freeranger.range.tasks.data.Task
import java.time.Clock
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * One exact alarm per task, keyed by task id, so rescheduling replaces it. Each alarm is for
 * the task's next pending time; when it fires, the receiver schedules the one after.
 */
class AlarmReminders(
    private val context: Context,
    /** When all-day tasks remind, from Settings. */
    private val allDayAt: suspend () -> LocalTime,
    private val clock: () -> Clock = { Clock.systemDefaultZone() },
) : Reminders {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    override suspend fun sync(task: Task) {
        val clock = clock()
        val (at, time) = task.nextReminder(LocalDateTime.now(clock), allDayAt()) ?: run {
            alarms.cancel(alarmIntent(task.id, null))
            return
        }
        val millis = at.atZone(clock.zone).toInstant().toEpochMilli()
        val intent = alarmIntent(task.id, ReminderReceiver.Moment(at.toLocalDate(), time))
        // Exact alarms come from USE_EXACT_ALARM, granted at install. The inexact fallback
        // only matters if a future Android version takes that away.
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        }
    }

    override fun cancel(taskId: Long) {
        alarms.cancel(alarmIntent(taskId, null))
        TaskNotifications.cancel(context, taskId)
    }

    // Extras don't take part in PendingIntent matching, so cancelling with null finds the same alarm.
    private fun alarmIntent(taskId: Long, moment: ReminderReceiver.Moment?): PendingIntent = PendingIntent.getBroadcast(
        context,
        taskId.toInt(),
        ReminderReceiver.intent(context, ReminderReceiver.ACTION_REMIND, taskId, moment),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
