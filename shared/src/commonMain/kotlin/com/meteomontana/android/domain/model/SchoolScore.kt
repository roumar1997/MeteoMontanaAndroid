package com.meteomontana.android.domain.model

data class SchoolScore(
    val id: String,
    val todayScore: Int,
    val hourlyScores: List<Int>,
    val dryRock: Boolean,
    val rainMm: Double = 0.0,
    val rainProb: Int = 0
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
