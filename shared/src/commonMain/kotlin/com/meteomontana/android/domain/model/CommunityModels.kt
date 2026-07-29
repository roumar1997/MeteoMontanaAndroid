package com.meteomontana.android.domain.model

/**
 * Votación comunitaria (2026-07-29): orientación de paredes y grado por
 * consenso. Modelos de dominio — las pantallas usan esto, no los DTOs.
 */

/** Resumen de orientación de una superficie (photoIndex null = piedra/sector entero). */
data class OrientationSummary(
    val photoIndex: Int?,
    val votes: Map<String, Int>,
    val consensus: String?,
    val myVote: String?
)

/** Resumen de grado de una vía: recuento, equipador, mostrado (consenso) y mi voto. */
data class GradeSummary(
    val lineId: String,
    val votes: Map<String, Int>,
    val setterGrade: String?,
    val displayedGrade: String?,
    val myVote: String?
)

/** Una franja horaria de sol de una pared. */
data class SunHour(val time: String, val inSun: Boolean)

/** Tira de sol del día (aspect null = sin orientación votada todavía). */
data class SunHours(val aspect: String?, val hours: List<SunHour>)
