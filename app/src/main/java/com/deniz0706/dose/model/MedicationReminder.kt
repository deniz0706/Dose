package com.deniz0706.dose.model

import kotlinx.serialization.Serializable

@Serializable
data class MedicationReminder(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val dose: String = "",
    val hour: Int,
    val minute: Int,
    val repeatDays: Set<Int> = (1..7).toSet(),
    val enabled: Boolean = true
)
