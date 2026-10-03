package com.deniz0706.dose.reminder

import android.content.Context
import com.deniz0706.dose.data.MedicationRepository
import com.deniz0706.dose.model.*
import java.time.LocalDate

object DoseActions {
    fun eventKey(reminderId:Long,timeId:Long,date:LocalDate=LocalDate.now())="$reminderId:$timeId:$date"
    fun event(reminder:MedicationReminder,timeId:Long,status:DoseStatus,scheduledDate:LocalDate=LocalDate.now()):DoseEvent {
        val time=reminder.effectiveTimes().firstOrNull{it.id==timeId}?:reminder.effectiveTimes().first()
        return DoseEvent(eventKey(reminder.id,time.id,scheduledDate),reminder.id,time.id,scheduledDate.toString(),time.hour,time.minute,status)
    }
    suspend fun taken(context:Context,reminder:MedicationReminder,timeId:Long,scheduledDate:LocalDate=LocalDate.now()){
        MedicationRepository(context).markTaken(reminder,event(reminder,timeId,DoseStatus.TAKEN,scheduledDate))
        NotificationHelper.cancel(context,reminder,timeId)
    }
    suspend fun snoozed(context:Context,reminder:MedicationReminder,timeId:Long,scheduledDate:LocalDate=LocalDate.now()){
        MedicationRepository(context).recordEvent(event(reminder,timeId,DoseStatus.SNOOZED,scheduledDate))
        NotificationHelper.cancel(context,reminder,timeId)
        ReminderScheduler.snooze(context,reminder,timeId,scheduledDate)
    }
}
