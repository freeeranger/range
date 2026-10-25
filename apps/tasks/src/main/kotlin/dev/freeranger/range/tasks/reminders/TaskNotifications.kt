package dev.freeranger.range.tasks.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.freeranger.range.tasks.MainActivity
import dev.freeranger.range.tasks.R
import dev.freeranger.range.tasks.data.Task
import dev.freeranger.range.tasks.ui.common.TaskFormat
import dev.freeranger.range.ui.theme.RangeAccents
import java.time.LocalDate
import java.time.ZoneId

object TaskNotifications {
    private const val CHANNEL_REMINDERS = "reminders"

    fun createChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL_REMINDERS, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.notification_channel_reminders))
            .setDescription(context.getString(R.string.notification_channel_reminders_description))
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /** Title is the task; the body is its details, or the day and time it is for. */
    fun show(context: Context, task: Task, moment: ReminderReceiver.Moment) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val format = TaskFormat(context)
        val body = task.details.ifBlank { format.moment(moment.date, moment.time, LocalDate.now()) }
        val requestCode = task.id.toInt()

        val open = PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java)
                .putExtra(ReminderReceiver.EXTRA_TASK_ID, task.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val complete = PendingIntent.getBroadcast(
            context,
            requestCode,
            ReminderReceiver.intent(context, ReminderReceiver.ACTION_COMPLETE, task.id, moment),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(RangeAccents.Tasks.light.primary.toArgb())
            .setContentTitle(task.title)
            .setContentText(body.takeIf { it.isNotEmpty() })
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(R.drawable.ic_check, context.getString(R.string.notification_complete), complete)
            // An all-day reminder is for the whole day, so it shows when it came.
            .setWhen(moment.time?.let { moment.date.atTime(it).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() } ?: System.currentTimeMillis())
            .setShowWhen(true)
            .build()

        NotificationManagerCompat.from(context).notify(requestCode, notification)
    }

    fun cancel(context: Context, taskId: Long) {
        NotificationManagerCompat.from(context).cancel(taskId.toInt())
    }
}
