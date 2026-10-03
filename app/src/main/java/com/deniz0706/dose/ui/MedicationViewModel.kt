package com.deniz0706.dose.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deniz0706.dose.data.MedicationRepository
import com.deniz0706.dose.model.*
import com.deniz0706.dose.reminder.DoseActions
import com.deniz0706.dose.reminder.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MedicationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository=MedicationRepository(application)
    val reminders=repository.reminders.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val events=repository.events.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    init { viewModelScope.launch { repository.reconcileMissed() } }
    fun save(reminder:MedicationReminder)=viewModelScope.launch{repository.save(reminder);ReminderScheduler.schedule(getApplication(),reminder)}
    fun toggle(reminder:MedicationReminder)=save(reminder.copy(enabled=!reminder.enabled))
    fun archive(reminder:MedicationReminder)=save(reminder.copy(archived=true,enabled=false))
    fun restore(reminder:MedicationReminder)=save(reminder.copy(archived=false))
    fun delete(reminder:MedicationReminder)=viewModelScope.launch{ReminderScheduler.cancel(getApplication(),reminder);repository.delete(reminder.id)}
    fun markTaken(reminder:MedicationReminder,timeId:Long)=viewModelScope.launch{DoseActions.taken(getApplication(),reminder,timeId)}
    suspend fun exportBackup():String=repository.exportBackup()
    suspend fun importBackup(raw:String){repository.importBackup(raw); repository.current().forEach{ReminderScheduler.schedule(getApplication(),it)}}
    fun undoTaken(reminder:MedicationReminder,timeId:Long)=viewModelScope.launch{
        repository.undoTaken(reminder, DoseActions.eventKey(reminder.id,timeId))
    }
}
