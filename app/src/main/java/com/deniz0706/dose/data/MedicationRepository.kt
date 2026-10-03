package com.deniz0706.dose.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.deniz0706.dose.model.MedicationReminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("dose_data")

class MedicationRepository(private val context: Context) {
    private val key = stringPreferencesKey("medications")
    private val json = Json { ignoreUnknownKeys = true }

    val reminders: Flow<List<MedicationReminder>> = context.dataStore.data.map { prefs ->
        prefs[key]?.let { raw ->
            runCatching { json.decodeFromString<List<MedicationReminder>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    suspend fun current(): List<MedicationReminder> = reminders.first()

    suspend fun save(reminder: MedicationReminder) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let {
                runCatching { json.decodeFromString<List<MedicationReminder>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            prefs[key] = json.encodeToString(current.filterNot { it.id == reminder.id } + reminder)
        }
    }

    suspend fun delete(id: Long) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let {
                runCatching { json.decodeFromString<List<MedicationReminder>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            prefs[key] = json.encodeToString(current.filterNot { it.id == id })
        }
    }
}
