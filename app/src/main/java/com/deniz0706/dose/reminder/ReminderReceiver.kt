package com.deniz0706.dose.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminder = ReminderScheduler.decode(intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER)) ?: return
        val timeId = intent.getLongExtra(ReminderScheduler.EXTRA_TIME_ID, reminder.effectiveTimes().first().id)
        val scheduledDate = intent.getStringExtra(ReminderScheduler.EXTRA_SCHEDULED_DATE)
        val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
            putExtra(ReminderScheduler.EXTRA_REMINDER, ReminderScheduler.encode(reminder))
            putExtra(ReminderScheduler.EXTRA_TIME_ID, timeId)
            scheduledDate?.let { putExtra(ReminderScheduler.EXTRA_SCHEDULED_DATE, it) }
        }
        androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
        runCatching {
            context.startActivity(Intent(context, ReminderAlertActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                putExtra(ReminderScheduler.EXTRA_REMINDER, ReminderScheduler.encode(reminder))
                putExtra(ReminderScheduler.EXTRA_TIME_ID, timeId)
                scheduledDate?.let { putExtra(ReminderScheduler.EXTRA_SCHEDULED_DATE, it) }
            })
        }
        if (intent.action == ReminderScheduler.ACTION_NORMAL && reminder.enabled) {
            val time = reminder.effectiveTimes().firstOrNull { it.id == timeId } ?: reminder.effectiveTimes().first()
            ReminderScheduler.scheduleTime(context, reminder, time)
        }
    }
}
