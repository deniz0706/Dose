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

    val reminders: Flow<List<MedicationReminder>> = context.dataStore.data.map { prefs ->
        decodeReminders(prefs[medicationsKey])
    }
    val events: Flow<List<DoseEvent>> = context.dataStore.data.map { prefs ->
        prefs[eventsKey]?.let { runCatching { json.decodeFromString<List<DoseEvent>>(it) }.getOrDefault(emptyList()) } ?: emptyList()
    }

    suspend fun current(): List<MedicationReminder> = reminders.first()

    suspend fun save(reminder: MedicationReminder) {
        context.dataStore.edit { prefs ->
            val current = decodeReminders(prefs[medicationsKey])
            prefs[medicationsKey] = json.encodeToString(current.filterNot { it.id == reminder.id } + reminder)
        }
    }

    suspend fun delete(id: Long) {
        context.dataStore.edit { prefs ->
            prefs[medicationsKey] = json.encodeToString(decodeReminders(prefs[medicationsKey]).filterNot { it.id == id })
        }
    }

    suspend fun recordEvent(event: DoseEvent) {
        context.dataStore.edit { prefs ->
            val current = prefs[eventsKey]?.let { runCatching { json.decodeFromString<List<DoseEvent>>(it) }.getOrDefault(emptyList()) } ?: emptyList()
            prefs[eventsKey] = json.encodeToString((current.filterNot { it.key == event.key } + event).takeLast(1000))
        }
    }

    suspend fun markTaken(reminder: MedicationReminder, event: DoseEvent) {
        context.dataStore.edit { prefs ->
            val currentEvents = prefs[eventsKey]?.let { runCatching { json.decodeFromString<List<DoseEvent>>(it) }.getOrDefault(emptyList()) } ?: emptyList()
            val alreadyTaken = currentEvents.any { it.key == event.key && it.status == DoseStatus.TAKEN }
            prefs[eventsKey] = json.encodeToString((currentEvents.filterNot { it.key == event.key } + event.copy(status = DoseStatus.TAKEN)).takeLast(1000))
            if (!alreadyTaken && reminder.stock != null) {
                val current = decodeReminders(prefs[medicationsKey])
                prefs[medicationsKey] = json.encodeToString(current.map {
                    if (it.id == reminder.id) it.copy(stock = (it.stock ?: 0).minus(1).coerceAtLeast(0)) else it
                })
            }
        }
    }

    private fun decodeReminders(raw: String?): List<MedicationReminder> =
        raw?.let { runCatching { json.decodeFromString<List<MedicationReminder>>(it) }.getOrDefault(emptyList()) } ?: emptyList()
}
