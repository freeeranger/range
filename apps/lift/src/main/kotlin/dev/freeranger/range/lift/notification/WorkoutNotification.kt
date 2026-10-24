package dev.freeranger.range.lift.notification

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.freeranger.range.lift.LiftApplication
import dev.freeranger.range.lift.MainActivity
import dev.freeranger.range.lift.R
import dev.freeranger.range.lift.data.LiftPreferences
import dev.freeranger.range.lift.data.LiftRepository
import dev.freeranger.range.lift.data.UpNext
import dev.freeranger.range.lift.data.WorkoutLog
import dev.freeranger.range.lift.data.isComplete
import dev.freeranger.range.lift.ui.common.LiftFormat
import dev.freeranger.range.ui.theme.RangeAccents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The workout in progress, outside the app. With the setting on, a notification stays for the
 * whole workout: the next set and the numbers it'll take, sets done so far, a clock counting the
 * workout or the rest since the last set, and a button ticking the next set off. With it off, it
 * shows only between sets, as the rest stopwatch. It follows the data, so it's right however the
 * workout changes, and the system draws the clock, so it keeps counting with the app closed.
 */
class WorkoutNotification(
    private val context: Context,
    private val repository: LiftRepository,
    private val preferences: LiftPreferences,
) {
    fun follow(scope: CoroutineScope) = scope.launch {
        combine(repository.inProgress, preferences.workoutNotification) { log, always -> log to always }
            .collectLatest { (log, always) -> show(log, always) }
    }

    private suspend fun show(log: WorkoutLog?, always: Boolean) {
        val workout = log?.workout
        val resting = workout?.lastSetAt
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (workout == null || (!always && resting == null) || !allowed) return cancel()

        val format = LiftFormat(context)
        val next = repository.upNext(log)
        val open = PendingIntent.getActivity(
            context,
            0,
            MainActivity.openWorkout(context, workout.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(RangeAccents.Lift.light.primary.toArgb())
            .setContentTitle(next?.let { format.name(it.logged.exercise) } ?: context.getString(R.string.notification_all_done))
            .setContentText(next?.let { describe(it, format) })
            .setSubText(if (resting != null) context.getString(R.string.rest_label) else format.name(workout))
            .setUsesChronometer(true)
            .setWhen((resting ?: workout.startedAt).toEpochMilli())
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
        if (always) {
            builder.setProgress(log.exercises.sumOf { it.sets.size }, log.doneSets.size, false)
            // Only a set with numbers to take can be ticked from here; otherwise it's typed in the app.
            if (next != null && next.set.isComplete(next.logged.exercise)) {
                val tick = PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(context, TickSetReceiver::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                builder.addAction(0, context.getString(R.string.notification_tick), tick)
            }
        }
        NotificationManagerCompat.from(context).notify(ID, builder.build())
    }

    /** "Set 2 of 3 · 80 kg × 5": where the next set falls in its exercise, and its numbers if it has them. */
    private fun describe(next: UpNext, format: LiftFormat): String {
        val position = context.getString(R.string.notification_set, next.index + 1, next.logged.sets.size)
        val exercise = next.logged.exercise
        return if (next.set.isComplete(exercise)) format.labelled(position, format.set(next.set, exercise)) else position
    }

    private fun cancel() = NotificationManagerCompat.from(context).cancel(ID)

    companion object {
        private const val CHANNEL = "workout"
        private const val ID = 1

        fun createChannel(context: Context) {
            val manager = NotificationManagerCompat.from(context)
            // Quiet, but not silent: silent notifications can be hidden from the lock screen.
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.notification_channel_workout))
                    .setSound(null, null)
                    .setVibrationEnabled(false)
                    .setShowBadge(false)
                    .build(),
            )
            // Earlier channels: the countdown timer's, then the rest stopwatch's.
            listOf("rest_timer", "rest_over", "rest_stopwatch").forEach(manager::deleteNotificationChannel)
        }
    }
}

/** The notification's button: ticks off the next set, as the app would with the numbers it shows. */
class TickSetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as LiftApplication).container
        val pending = goAsync()
        container.scope.launch {
            try {
                container.repository.tickNext()
            } finally {
                pending.finish()
            }
        }
    }
}
