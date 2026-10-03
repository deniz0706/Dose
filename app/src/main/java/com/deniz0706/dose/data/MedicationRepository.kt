package com.deniz0706.dose.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.deniz0706.dose.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("dose_data")

class MedicationRepository(private val context: Context) {
    private val medicationsKey = stringPreferencesKey("medications")
    private val eventsKey = stringPreferencesKey("dose_events")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val reminders: Flow<List<MedicationReminder>> = context.dataStore.data.map { decodeReminders(it[medicationsKey]) }
    val events: Flow<List<DoseEvent>> = context.dataStore.data.map { prefs -> decodeEvents(prefs[eventsKey]) }
    suspend fun current(): List<MedicationReminder> = reminders.first()

    suspend fun save(reminder: MedicationReminder) = context.dataStore.edit { prefs ->
        val current = decodeReminders(prefs[medicationsKey])
        prefs[medicationsKey] = json.encodeToString(current.filterNot { it.id == reminder.id } + reminder)
    }

    suspend fun delete(id: Long) = context.dataStore.edit { prefs ->
        prefs[medicationsKey] = json.encodeToString(decodeReminders(prefs[medicationsKey]).filterNot { it.id == id })
    }

    suspend fun reconcileMissed(now: java.time.ZonedDateTime = java.time.ZonedDateTime.now()) = context.dataStore.edit { prefs ->
        val reminders = decodeReminders(prefs[medicationsKey])
        val currentEvents = decodeEvents(prefs[eventsKey]).toMutableList()
        val start = now.toLocalDate().minusDays(30)
        var date = start
        while (!date.isAfter(now.toLocalDate())) {
            val day = date.dayOfWeek.value
            reminders.filter { it.enabled && (it.repeatDays.isEmpty() || day in it.repeatDays) }.forEach { reminder ->
                reminder.effectiveTimes().forEach { time ->
                    val scheduled = date.atTime(time.hour, time.minute).atZone(now.zone)
                    if (scheduled.isBefore(now)) {
                        val key = "${reminder.id}:${time.id}:$date"
                        if (currentEvents.none { it.key == key }) currentEvents += DoseEvent(
                            key, reminder.id, time.id, date.toString(), time.hour, time.minute, DoseStatus.MISSED, reminder.name
                        )
                    }
                }
            }
            date = date.plusDays(1)
        }
        prefs[eventsKey] = json.encodeToString(currentEvents.takeLast(1500))
    }

    suspend fun recordEvent(event: DoseEvent) = context.dataStore.edit { prefs ->
        val current = decodeEvents(prefs[eventsKey])
        prefs[eventsKey] = json.encodeToString((current.filterNot { it.key == event.key } + event).takeLast(1500))
    }

    suspend fun markTaken(reminder: MedicationReminder, event: DoseEvent) = context.dataStore.edit { prefs ->
        val currentEvents = decodeEvents(prefs[eventsKey])
        val alreadyTaken = currentEvents.any { it.key == event.key && it.status == DoseStatus.TAKEN }
        prefs[eventsKey] = json.encodeToString((currentEvents.filterNot { it.key == event.key } + event.copy(status = DoseStatus.TAKEN)).takeLast(1500))
        if (!alreadyTaken && reminder.stock != null) {
            prefs[medicationsKey] = json.encodeToString(decodeReminders(prefs[medicationsKey]).map {
                if (it.id == reminder.id) it.copy(stock = (it.stock ?: 0).minus(1).coerceAtLeast(0)) else it
            })
        }
    }

    suspend fun undoTaken(reminder: MedicationReminder, key: String) = context.dataStore.edit { prefs ->
        val currentEvents = decodeEvents(prefs[eventsKey])
        val wasTaken = currentEvents.any { it.key == key && it.status == DoseStatus.TAKEN }
        prefs[eventsKey] = json.encodeToString(currentEvents.filterNot { it.key == key })
        if (wasTaken && reminder.stock != null) {
            prefs[medicationsKey] = json.encodeToString(decodeReminders(prefs[medicationsKey]).map {
                if (it.id == reminder.id) it.copy(stock = (it.stock ?: 0) + 1) else it
            })
        }
    }

    private fun decodeReminders(raw:String?) = raw?.let { runCatching { json.decodeFromString<List<MedicationReminder>>(it) }.getOrDefault(emptyList()) } ?: emptyList()
    private fun decodeEvents(raw:String?) = raw?.let { runCatching { json.decodeFromString<List<DoseEvent>>(it) }.getOrDefault(emptyList()) } ?: emptyList()
}
