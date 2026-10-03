package com.deniz0706.dose.model

import kotlinx.serialization.Serializable

@Serializable
data class MedicationTime(val id: Long = System.nanoTime(), val hour: Int, val minute: Int)

@Serializable
data class MedicationReminder(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val dose: String = "",
    val hour: Int = 9,
    val minute: Int = 0,
    val repeatDays: Set<Int> = (1..7).toSet(),
    val enabled: Boolean = true,
    val times: List<MedicationTime> = emptyList(),
    val note: String = "",
    val snoozeMinutes: Int = 10,
    val stock: Int? = null,
    val lowStockThreshold: Int = 5
) {
    fun effectiveTimes(): List<MedicationTime> =
        if (times.isNotEmpty()) times else listOf(MedicationTime(id = id, hour = hour, minute = minute))
}

@Serializable enum class DoseStatus { TAKEN, SNOOZED, MISSED }

@Serializable
data class DoseEvent(
    val key: String,
    val reminderId: Long,
    val timeId: Long,
    val scheduledDate: String,
    val scheduledHour: Int,
    val scheduledMinute: Int,
    val status: DoseStatus,
    val medicationName: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
