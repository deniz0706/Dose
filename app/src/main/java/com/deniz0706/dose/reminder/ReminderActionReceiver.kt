package com.deniz0706.dose.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class ReminderActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_TAKEN = "com.deniz0706.dose.TAKEN"
        const val ACTION_SNOOZE = "com.deniz0706.dose.ACTION_SNOOZE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminder = ReminderScheduler.decode(intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER)) ?: return
        val timeId = intent.getLongExtra(ReminderScheduler.EXTRA_TIME_ID, reminder.effectiveTimes().first().id)
        val scheduledDate = intent.getStringExtra(ReminderScheduler.EXTRA_SCHEDULED_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (intent.action == ACTION_TAKEN) DoseActions.taken(context, reminder, timeId, scheduledDate)
                else if (intent.action == ACTION_SNOOZE) DoseActions.snoozed(context, reminder, timeId, scheduledDate)
            } finally { pending.finish() }
        }
    }
}
