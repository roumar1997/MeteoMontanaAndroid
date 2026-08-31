package com.meteomontana.android.domain.model

/** Boletín de montaña de AEMET para un macizo (mismos campos que el DTO). */
data class MountainBulletin(
    val area: String,
    val areaName: String,
    val day: Int,
    /** nubosidad, pcp, tormentas, temperatura, viento, isocero, iso10, v1500, v3000. */
    val texts: Map<String, String>,
    val spots: List<MountainSpot>
)

data class MountainSpot(
    val nombre: String,
    val altitud: String,
    val minima: Int,
    val maxima: Int
)
