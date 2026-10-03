package com.deniz0706.dose.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminder = ReminderScheduler.decode(intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER)) ?: return
        val timeId = intent.getLongExtra(ReminderScheduler.EXTRA_TIME_ID, reminder.effectiveTimes().first().id)
        NotificationHelper.show(context, reminder, timeId)
        if (intent.action == ReminderScheduler.ACTION_NORMAL && reminder.enabled) {
            val time = reminder.effectiveTimes().firstOrNull { it.id == timeId } ?: reminder.effectiveTimes().first()
            ReminderScheduler.scheduleTime(context, reminder, time)
        }
    }
}
