package com.deniz0706.dose.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_TAKEN = "com.deniz0706.dose.TAKEN"
        const val ACTION_SNOOZE = "com.deniz0706.dose.ACTION_SNOOZE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminder = ReminderScheduler.decode(intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER)) ?: return
        NotificationHelper.cancel(context, reminder)
        if (intent.action == ACTION_SNOOZE) {
            ReminderScheduler.snooze(context, reminder, 10)
        }
    }
}
