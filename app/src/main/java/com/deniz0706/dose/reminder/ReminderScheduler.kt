package com.deniz0706.dose.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.deniz0706.dose.model.MedicationReminder
import com.deniz0706.dose.model.MedicationTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.ZonedDateTime

object ReminderScheduler {
    const val ACTION_NORMAL = "com.deniz0706.dose.NORMAL"
    const val ACTION_SNOOZE = "com.deniz0706.dose.SNOOZE"
    const val EXTRA_REMINDER = "reminder"
    const val EXTRA_TIME_ID = "time_id"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(reminder: MedicationReminder): String = json.encodeToString(reminder)
    fun decode(raw: String?): MedicationReminder? = raw?.let { runCatching { json.decodeFromString<MedicationReminder>(it) }.getOrNull() }

    fun schedule(context: Context, reminder: MedicationReminder) {
        cancel(context, reminder)
        if (!reminder.enabled) return
        reminder.effectiveTimes().forEach { scheduleTime(context, reminder, it) }
    }

    fun scheduleTime(context: Context, reminder: MedicationReminder, time: MedicationTime) {
        if (!reminder.enabled) return
        val at = nextOccurrence(reminder, time).toInstant().toEpochMilli()
        scheduleAt(context, reminder, time, at, false)
    }

    fun snooze(context: Context, reminder: MedicationReminder, timeId: Long, minutes: Int = reminder.snoozeMinutes) {
        val time = reminder.effectiveTimes().firstOrNull { it.id == timeId } ?: reminder.effectiveTimes().first()
        scheduleAt(context, reminder, time, System.currentTimeMillis() + minutes.coerceIn(1, 120) * 60_000L, true)
    }

    fun cancel(context: Context, reminder: MedicationReminder) {
        val manager = context.getSystemService(AlarmManager::class.java)
        reminder.effectiveTimes().forEach { time ->
            manager.cancel(pendingIntent(context, reminder, time, false))
            manager.cancel(pendingIntent(context, reminder, time, true))
        }
    }

    private fun scheduleAt(context: Context, reminder: MedicationReminder, time: MedicationTime, at: Long, snooze: Boolean) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context, reminder, time, snooze)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        if (canExact) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun pendingIntent(context: Context, reminder: MedicationReminder, time: MedicationTime, snooze: Boolean): PendingIntent {
        val base = 31 * reminder.id.hashCode() + time.id.hashCode()
        val requestCode = base xor if (snooze) 0x5A000000 else 0
        return PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, ReminderReceiver::class.java).apply {
                action = if (snooze) ACTION_SNOOZE else ACTION_NORMAL
                putExtra(EXTRA_REMINDER, encode(reminder))
                putExtra(EXTRA_TIME_ID, time.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun nextOccurrence(reminder: MedicationReminder, time: MedicationTime = reminder.effectiveTimes().first(), now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
        val base = now.withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
        for (offset in 0..7) {
            val day = base.plusDays(offset.toLong())
            val allowed = reminder.repeatDays.isEmpty() || day.dayOfWeek.value in reminder.repeatDays
            if (allowed && day.isAfter(now)) return day
        }
        return base.plusDays(1)
    }
}
