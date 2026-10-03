package com.deniz0706.dose.reminder

import android.app.*
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.deniz0706.dose.model.MedicationReminder

object NotificationHelper {
    const val CHANNEL_ID = "medication_reminders"

    fun createNotificationChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "İlaç hatırlatmaları", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "İlaç saatleri için hatırlatmalar"
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
    }

    fun show(context: Context, reminder: MedicationReminder, timeId: Long) {
        createNotificationChannel(context)
        val id = notificationId(reminder, timeId)
        val encoded = ReminderScheduler.encode(reminder)
        fun baseIntent(target: Class<*>) = Intent(context, target).apply {
            putExtra(ReminderScheduler.EXTRA_REMINDER, encoded)
            putExtra(ReminderScheduler.EXTRA_TIME_ID, timeId)
        }
        val full = PendingIntent.getActivity(context, id xor 0x21000000,
            baseIntent(ReminderAlertActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val taken = PendingIntent.getBroadcast(context, id xor 0x31000000,
            baseIntent(ReminderActionReceiver::class.java).apply { action = ReminderActionReceiver.ACTION_TAKEN },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val snooze = PendingIntent.getBroadcast(context, id xor 0x32000000,
            baseIntent(ReminderActionReceiver::class.java).apply { action = ReminderActionReceiver.ACTION_SNOOZE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val body = listOf(reminder.dose, reminder.note).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "İlacını almayı unutma." }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("${reminder.name} zamanı").setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setOngoing(true).setAutoCancel(false)
            .setContentIntent(full).setFullScreenIntent(full, true)
            .addAction(0, "Aldım", taken).addAction(0, "${reminder.snoozeMinutes} dk sonra", snooze).build()
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    fun cancel(context: Context, reminder: MedicationReminder, timeId: Long? = null) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (timeId != null) manager.cancel(notificationId(reminder, timeId))
        else reminder.effectiveTimes().forEach { manager.cancel(notificationId(reminder, it.id)) }
    }

    private fun notificationId(reminder: MedicationReminder, timeId: Long) = 31 * reminder.id.hashCode() + timeId.hashCode()
}
