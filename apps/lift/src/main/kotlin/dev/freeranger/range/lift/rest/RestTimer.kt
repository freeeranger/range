package dev.freeranger.range.lift.rest

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.freeranger.range.lift.LiftApplication
import dev.freeranger.range.lift.MainActivity
import dev.freeranger.range.lift.R
import dev.freeranger.range.lift.data.RestAlarm
import dev.freeranger.range.lift.data.Workout
import dev.freeranger.range.ui.theme.RangeAccents
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * The rest timer outside the app. While a rest runs, a notification counts down on its own (the
 * system draws the clock, so it keeps going on the lock screen with the app closed), with +30 s
 * and Skip. An exact alarm fires when it ends and swaps it for an alert with sound and vibration.
 */
class AndroidRestAlarm(private val context: Context) : RestAlarm {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    /** What the rest is after, kept for when it's lengthened from the notification and for the alert at the end. */
    var label: String? = null
        private set

    override fun sync(workout: Workout, label: String?) {
        if (label != null) this.label = label
        val endsAt = workout.restEndsAt ?: return cancel()
        val millis = endsAt.toEpochMilli()
        val intent = alarmIntent(RestReceiver.intent(context, RestReceiver.ACTION_OVER, workout.id).putExtra(RestReceiver.EXTRA_ENDS_AT, millis))
        // Exact alarms come from USE_EXACT_ALARM, granted at install, as in Tasks.
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        }
        RestNotifications.showCountdown(context, workout.id, endsAt, this.label)
    }

    override fun cancel() {
        alarms.cancel(alarmIntent(RestReceiver.intent(context, RestReceiver.ACTION_OVER, 0)))
        RestNotifications.cancel(context)
    }

    // Extras don't take part in PendingIntent matching, so there's only ever one rest alarm.
    private fun alarmIntent(intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

object RestNotifications {
    private const val CHANNEL_TIMER = "rest_timer"
    private const val CHANNEL_OVER = "rest_over"
    private const val ID = 1

    /** What the countdown's + button adds. */
    const val ADD_SECONDS = 30

    fun createChannels(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        // Counts down quietly; it still shows on the lock screen, unlike a silent one.
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_TIMER, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notification_channel_timer))
                .setDescription(context.getString(R.string.notification_channel_timer_description))
                .setSound(null, null)
                .setVibrationEnabled(false)
                .setShowBadge(false)
                .build(),
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_OVER, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.notification_channel_over))
                .setDescription(context.getString(R.string.notification_channel_over_description))
                .setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build(),
                )
                .setVibrationEnabled(true)
                .setVibrationPattern(longArrayOf(0, 250, 150, 250))
                .build(),
        )
    }

    fun showCountdown(context: Context, workoutId: Long, endsAt: Instant, label: String?) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val left = endsAt.toEpochMilli() - System.currentTimeMillis()
        if (left <= 0) return
        val notification = base(context, CHANNEL_TIMER, workoutId)
            .setContentTitle(context.getString(R.string.rest_notification_title))
            .setContentText(label)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endsAt.toEpochMilli())
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            // Gone at the end even if the alarm comes late; the alert replaces it anyway.
            .setTimeoutAfter(left)
            .addAction(0, context.getString(R.string.rest_add, ADD_SECONDS), action(context, RestReceiver.ACTION_ADD, workoutId))
            .addAction(0, context.getString(R.string.rest_skip), action(context, RestReceiver.ACTION_SKIP, workoutId))
            .build()
        NotificationManagerCompat.from(context).notify(ID, notification)
    }

    fun showOver(context: Context, workoutId: Long, label: String?) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val notification = base(context, CHANNEL_OVER, workoutId)
            .setContentTitle(context.getString(R.string.rest_over_title))
            .setContentText(label)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            // A reminder to lift, not something to keep: it clears itself after a couple of minutes.
            .setTimeoutAfter(120_000)
            .build()
        NotificationManagerCompat.from(context).notify(ID, notification)
    }

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(ID)

    private fun base(context: Context, channel: String, workoutId: Long) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_notification)
        .setColor(RangeAccents.Lift.light.primary.toArgb())
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setContentIntent(
            PendingIntent.getActivity(
                context,
                0,
                MainActivity.openWorkout(context, workoutId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )

    private fun action(context: Context, action: String, workoutId: Long) = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        RestReceiver.intent(context, action, workoutId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Ends the rest when its alarm fires, and handles the countdown's +30 s and Skip. */
class RestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val workoutId = intent.getLongExtra(EXTRA_WORKOUT_ID, -1)
        if (workoutId < 0) return
        val container = (context.applicationContext as LiftApplication).container
        val pending = goAsync()
        container.scope.launch {
            try {
                when (intent.action) {
                    ACTION_OVER -> {
                        // Skip it if the rest was changed or skipped since the alarm was set.
                        val endsAt = intent.getLongExtra(EXTRA_ENDS_AT, -1)
                        val workout = container.repository.workout(workoutId)
                        if (workout != null && !workout.isFinished && workout.restEndsAt?.toEpochMilli() == endsAt) {
                            RestNotifications.showOver(context, workoutId, container.restAlarm.label)
                        }
                    }
                    ACTION_ADD -> container.repository.addRest(workoutId, RestNotifications.ADD_SECONDS)
                    ACTION_SKIP -> container.repository.skipRest(workoutId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_OVER = "dev.freeranger.range.lift.REST_OVER"
        const val ACTION_ADD = "dev.freeranger.range.lift.REST_ADD"
        const val ACTION_SKIP = "dev.freeranger.range.lift.REST_SKIP"
        const val EXTRA_WORKOUT_ID = "workoutId"
        const val EXTRA_ENDS_AT = "endsAt"

        fun intent(context: Context, action: String, workoutId: Long): Intent =
            Intent(context, RestReceiver::class.java).setAction(action).putExtra(EXTRA_WORKOUT_ID, workoutId)
    }
}
