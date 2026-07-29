package com.meteomontana.android.domain.model

/** Estado completo de la alerta de quedadas, con todos los filtros configurables. */
data class MeetupAlertState(
    val enabled: Boolean,
    val daysCsv: String? = null,
    val schoolId: String? = null,
    val schoolName: String? = null,
    val discipline: String? = null,    // BOULDER | ROUTE | BOTH | null = cualquiera
    val privacy: String? = null,       // OPEN | FOLLOWERS | WOMEN | null = cualquiera
    val maxDistanceKm: Int? = null,
    val userLat: Double? = null,
    val userLon: Double? = null
)
