package com.deniz0706.dose.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.deniz0706.dose.model.MedicationReminder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.ZonedDateTime

object ReminderScheduler {
    const val ACTION_NORMAL = "com.deniz0706.dose.NORMAL"
    const val ACTION_SNOOZE = "com.deniz0706.dose.SNOOZE"
    const val EXTRA_REMINDER = "reminder"
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(reminder: MedicationReminder): String = json.encodeToString(reminder)
    fun decode(raw: String?): MedicationReminder? =
        raw?.let { runCatching { json.decodeFromString<MedicationReminder>(it) }.getOrNull() }

    fun schedule(context: Context, reminder: MedicationReminder) {
        cancel(context, reminder)
        if (!reminder.enabled) return
        val whenMillis = nextOccurrence(reminder).toInstant().toEpochMilli()
        scheduleAt(context, reminder, whenMillis, false)
    }

    fun snooze(context: Context, reminder: MedicationReminder, minutes: Long = 10) {
        val whenMillis = System.currentTimeMillis() + minutes * 60_000L
        scheduleAt(context, reminder, whenMillis, true)
    }

    fun cancel(context: Context, reminder: MedicationReminder) {
        val manager = context.getSystemService(AlarmManager::class.java)
        manager.cancel(pendingIntent(context, reminder, false))
        manager.cancel(pendingIntent(context, reminder, true))
    }

    private fun scheduleAt(context: Context, reminder: MedicationReminder, at: Long, snooze: Boolean) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context, reminder, snooze)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        if (canExact) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun pendingIntent(context: Context, reminder: MedicationReminder, snooze: Boolean): PendingIntent {
        val requestCode = reminder.id.hashCode() xor if (snooze) 0x5A000000 else 0
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ReminderReceiver::class.java).apply {
                action = if (snooze) ACTION_SNOOZE else ACTION_NORMAL
                putExtra(EXTRA_REMINDER, encode(reminder))
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun nextOccurrence(reminder: MedicationReminder, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
        var candidate = now.withHour(reminder.hour).withMinute(reminder.minute).withSecond(0).withNano(0)
        for (offset in 0..7) {
            val day = candidate.plusDays(offset.toLong())
            val isoDay = day.dayOfWeek.value
            val dayAllowed = reminder.repeatDays.isEmpty() || isoDay in reminder.repeatDays
            if (dayAllowed && day.isAfter(now)) return day
        }
        return candidate.plusDays(1)
    }
}
