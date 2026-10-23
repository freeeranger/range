package dev.freeranger.range.lift.rest

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.freeranger.range.lift.MainActivity
import dev.freeranger.range.lift.R
import dev.freeranger.range.lift.data.RestStopwatch
import dev.freeranger.range.lift.data.Workout
import dev.freeranger.range.ui.theme.RangeAccents

/**
 * The rest stopwatch outside the app: a quiet notification whose clock counts up from the last
 * set. The system draws the clock, so it keeps going on the lock screen with the app closed.
 */
class AndroidRestStopwatch(private val context: Context) : RestStopwatch {
    override fun show(workout: Workout, label: String) {
        val since = workout.lastSetAt ?: return cancel()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            MainActivity.openWorkout(context, workout.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(RangeAccents.Lift.light.primary.toArgb())
            .setContentTitle(context.getString(R.string.rest_label))
            .setContentText(label)
            .setUsesChronometer(true)
            .setWhen(since.toEpochMilli())
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(ID, notification)
    }

    override fun cancel() = NotificationManagerCompat.from(context).cancel(ID)

    companion object {
        private const val CHANNEL = "rest_stopwatch"
        private const val ID = 1

        fun createChannel(context: Context) {
            val manager = NotificationManagerCompat.from(context)
            // Quiet, but not silent: silent notifications can be hidden from the lock screen.
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.notification_channel_stopwatch))
                    .setSound(null, null)
                    .setVibrationEnabled(false)
                    .setShowBadge(false)
                    .build(),
            )
            // The countdown timer's channels, from before the stopwatch.
            listOf("rest_timer", "rest_over").forEach(manager::deleteNotificationChannel)
        }
    }
}
