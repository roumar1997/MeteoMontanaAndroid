package com.meteomontana.android.domain.model

data class SchoolScore(
    val id: String,
    val todayScore: Int,
    val hourlyScores: List<Int>,
    val dryRock: Boolean,
    val rainMm: Double = 0.0,
    val rainProb: Int = 0
)

/**
 * Lluvia y nota hora a hora de una escuela para las próximas 48 h (modo «Previsión» del Radar). [startTime] es la hora
 * local ISO de la primera posición ("2026-10-22T18:00"); las tres listas van alineadas, una posición por hora.
 */
data class SchoolRainOutlook(
    val id: String,
    val startTime: String?,
    val rainMm: List<Double>,
    val rainProb: List<Int>,
    val scores: List<Int>
)

/** Score de un tramo de días elegidos (selector de días de la lista). */
data class RangeScore(
    val id: String,
    val combinedScore: Int,        // media de días − penalización por lluvia
    val avgScore: Int,             // media simple (sin penalizar)
    val days: List<RangeDayScore>,
    val rainDays: Int,
    val maxRainMm: Double
)

data class RangeDayScore(
    val date: String,
    val score: Int,
    val rainMm: Double,
    val rainProb: Int,
    val rainy: Boolean
)
