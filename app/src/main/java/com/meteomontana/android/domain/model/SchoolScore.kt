package com.meteomontana.android.domain.model

data class SchoolScore(
    val id: String,
    val todayScore: Int,
    val hourlyScores: List<Int>,
    val dryRock: Boolean,
    val rainMm: Double = 0.0,
    val rainProb: Int = 0
)
