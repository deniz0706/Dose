package com.deniz0706.dose.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.deniz0706.dose.data.MedicationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MedicationRepository(context.applicationContext).current()
                    .filter { it.enabled }
                    .forEach { ReminderScheduler.schedule(context, it) }
            } finally {
                pending.finish()
            }
        }
    }
}
