package com.meteomontana.android.data.api.dto

import kotlinx.serialization.Serializable

/**
 * Preferencias de la "alerta del finde" (GET/PUT /api/me/weekend-alert).
 * notifyDay en ISO-8601: 1=lunes .. 7=domingo. notifyHour 0-23 (Europe/Madrid).
 */
@Serializable
data class WeekendAlertDto(
    val enabled: Boolean,
    val notifyDay: Int,
    val notifyHour: Int,
    val schoolIds: List<String>,
    /** SCHOOLS = escuelas elegidas a mano; NEARBY = las mejores en un radio. */
    val mode: String = "SCHOOLS",
    val radiusKm: Int? = null,
    val lat: Double? = null,
    val lon: Double? = null
)
