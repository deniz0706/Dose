package com.deniz0706.dose.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.deniz0706.dose.model.MedicationReminder

object NotificationHelper {
    const val CHANNEL_ID = "medication_reminders"

    fun createNotificationChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "İlaç hatırlatmaları",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "İlaç saatleri için hatırlatmalar"
            enableVibration(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context, reminder: MedicationReminder) {
        createNotificationChannel(context)
        val id = reminder.id.hashCode()
        val encoded = ReminderScheduler.encode(reminder)

        val alertIntent = Intent(context, ReminderAlertActivity::class.java).apply {
            putExtra(ReminderScheduler.EXTRA_REMINDER, encoded)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenIntent = PendingIntent.getActivity(
            context, id xor 0x21000000, alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val takenIntent = PendingIntent.getBroadcast(
            context, id xor 0x31000000,
            Intent(context, ReminderActionReceiver::class.java).apply {
                action = ReminderActionReceiver.ACTION_TAKEN
                putExtra(ReminderScheduler.EXTRA_REMINDER, encoded)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = PendingIntent.getBroadcast(
            context, id xor 0x32000000,
            Intent(context, ReminderActionReceiver::class.java).apply {
                action = ReminderActionReceiver.ACTION_SNOOZE
                putExtra(ReminderScheduler.EXTRA_REMINDER, encoded)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = reminder.dose.ifBlank { "İlacını almayı unutma." }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("${reminder.name} zamanı")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .addAction(0, "Aldım", takenIntent)
            .addAction(0, "10 dk sonra", snoozeIntent)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    fun cancel(context: Context, reminder: MedicationReminder) {
        context.getSystemService(NotificationManager::class.java).cancel(reminder.id.hashCode())
    }
}
