package com.popemkt.watchcal.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.popemkt.watchcal.R
import com.popemkt.watchcal.domain.EventInstance
import java.util.Date

/**
 * Builds the snooze-until-done notification. Swipe-away (deleteIntent) routes
 * to Snooze — only the explicit Done action dismisses (specs/00-product.md).
 *
 * [fullScreenIntent] is injected by the composition root so this layer never
 * imports the ui alarm activity (specs/02 fence: reminders ↛ ui).
 */
class ReminderNotifier(
    private val context: Context,
    private val fullScreenIntent: (EventInstance) -> PendingIntent,
) {

    fun ensureChannel() {
        // Default sound on purpose: a custom alarm-stream sound got the whole alert
        // suppressed on the Xiaomi Watch 5 (see TODO NGH: in specs/01 § Notifications).
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 400)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
        LEGACY_CHANNEL_IDS.forEach(manager::deleteNotificationChannel)
    }

    fun show(instance: EventInstance) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val snooze = actionIntent(instance, ReminderActionReceiver.ACTION_SNOOZE)
        val done = actionIntent(instance, ReminderActionReceiver.ACTION_DONE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle(instance.title)
            .setContentText(timeLabel(instance))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Screen off → system launches the full-screen alarm; screen in use → heads-up only.
            .setFullScreenIntent(fullScreenIntent(instance), true)
            // Background refreshes re-post still-due reminders without re-buzzing;
            // a snooze cancels first, so its return buzzes again (specs/01-architecture.md).
            .setOnlyAlertOnce(true)
            .addAction(0, context.getString(R.string.action_snooze), snooze)
            .addAction(0, context.getString(R.string.action_done), done)
            .setDeleteIntent(snooze)
            .build()
            // Loop channel sound + vibration until the notification is cancelled —
            // the ring is independent of the full-screen takeover (specs/00 § Alarm-style alert).
            .apply { flags = flags or Notification.FLAG_INSISTENT }

        NotificationManagerCompat.from(context).notify(notificationId(instance), notification)
    }

    fun cancel(instanceKey: String) {
        NotificationManagerCompat.from(context).cancel(instanceKey.hashCode())
    }

    private fun notificationId(instance: EventInstance): Int = instance.instanceKey.hashCode()

    private fun timeLabel(instance: EventInstance): String =
        DateFormat.getTimeFormat(context).format(Date(instance.beginMillis))

    private fun actionIntent(instance: EventInstance, action: String): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(action)
            .putExtra(ReminderActionReceiver.EXTRA_INSTANCE_KEY, instance.instanceKey)
        return PendingIntent.getBroadcast(
            context,
            // Distinct request code per (instance, action) so PendingIntents don't collide.
            (instance.instanceKey + action).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "reminders_v3"
        private val LEGACY_CHANNEL_IDS = listOf("reminders", "reminders_alarm")
    }
}
