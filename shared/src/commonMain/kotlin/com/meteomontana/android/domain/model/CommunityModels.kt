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
    val myVote: String?,
    /** Votos separados por altura: "TALL" (más de 1,70 m), "SHORT" (menos) y "NONE" (sin dato). Vacío con un servidor antiguo. */
    val votesByHeight: Map<String, Map<String, Int>> = emptyMap(),
    /** Los dos grados preferidos cuando la altura parte la opinión; null si no hay desacuerdo suficiente. */
    val heightSplit: HeightSplit? = null,
    /** El grupo de altura de QUIEN CONSULTA ("TALL", "SHORT", "NONE") o null si aún no lo ha elegido. */
    val myHeight: String? = null
)

/** Cuando la gente más alta y la más baja votan un grado muy distinto, el grado se "parte" en dos. */
data class HeightSplit(val tallGrade: String, val shortGrade: String)

/** Una franja horaria de sol de una pared. */
data class SunHour(val time: String, val inSun: Boolean)

/** Tira de sol del día (aspect null = sin orientación votada todavía). */
data class SunHours(val aspect: String?, val hours: List<SunHour>)
