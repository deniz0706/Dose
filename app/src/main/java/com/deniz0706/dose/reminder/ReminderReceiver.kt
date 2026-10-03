package com.deniz0706.dose.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminder = ReminderScheduler.decode(intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER)) ?: return
        NotificationHelper.show(context, reminder)
        if (intent.action == ReminderScheduler.ACTION_NORMAL && reminder.enabled) {
            ReminderScheduler.schedule(context, reminder)
        }
    }
}
