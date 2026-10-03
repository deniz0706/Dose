package com.deniz0706.dose.reminder

import android.content.Context
import com.deniz0706.dose.data.MedicationRepository
import com.deniz0706.dose.model.*
import java.time.LocalDate

object DoseActions {
    fun eventKey(reminderId: Long, timeId: Long, date: LocalDate = LocalDate.now()) = "$reminderId:$timeId:$date"

    fun event(reminder: MedicationReminder, timeId: Long, status: DoseStatus): DoseEvent {
        val time = reminder.effectiveTimes().firstOrNull { it.id == timeId } ?: reminder.effectiveTimes().first()
        return DoseEvent(
            key = eventKey(reminder.id, time.id),
            reminderId = reminder.id,
            timeId = time.id,
            scheduledDate = LocalDate.now().toString(),
            scheduledHour = time.hour,
            scheduledMinute = time.minute,
            status = status
        )
    }

    suspend fun taken(context: Context, reminder: MedicationReminder, timeId: Long) {
        MedicationRepository(context).markTaken(reminder, event(reminder, timeId, DoseStatus.TAKEN))
        NotificationHelper.cancel(context, reminder)
    }

    suspend fun snoozed(context: Context, reminder: MedicationReminder, timeId: Long) {
        MedicationRepository(context).recordEvent(event(reminder, timeId, DoseStatus.SNOOZED))
        NotificationHelper.cancel(context, reminder)
        ReminderScheduler.snooze(context, reminder, timeId)
    }
}
