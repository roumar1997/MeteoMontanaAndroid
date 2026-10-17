package com.meteomontana.android.domain.model

/** Nota de una hora para la pared de un sector y si le da el sol directo. */
data class SectorHour(val hour: Int, val score: Int, val sun: Boolean)

/**
 * Mejor hora de un sector un día concreto (de 8 a 20 h): su orientación, la nota de cada hora y la mejor franja
 * seguida ([bestFrom]–[bestTo], ambas incluidas). Lo calcula el servidor con el sol sobre la pared y el tiempo.
 */
data class SectorHours(
    val sectorId: String,
    val name: String,
    val aspect: String,
    val hours: List<SectorHour>,
    val bestFrom: Int?,
    val bestTo: Int?,
    val bestScore: Int?,
)
