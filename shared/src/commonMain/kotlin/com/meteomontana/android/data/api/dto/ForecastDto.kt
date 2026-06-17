package com.meteomontana.android.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ForecastDto(
    val schoolId: String,
    val schoolName: String,
    val lat: Double,
    val lon: Double,
    val current: CurrentDto,
    val hours: List<HourForecastDto>,
    val days: List<DayForecastDto>,
    val bestDay: BestDayDto? = null,
    val bestWindow: OptimalWindowDto? = null
)

@Serializable
data class CurrentDto(
    val time: String,
    val temperature: Double,
    val humidity: Double,
    val windSpeed: Double,
    val precipitation: Double,
    val precipitationProbability: Int,
    val cloudCover: Int,
    val dewPoint: Double? = null,
    val precip24h: Double,
    val precip72h: Double,
    val dryRock: Boolean,
    val score: Int,
    val scoreLabel: String,
    val factors: List<ScoreFactorDto>,
    /** Estimación de secado tras lluvia (null en backends antiguos). */
    val drying: RockDryingDto? = null
)

@Serializable
data class RockDryingDto(
    val wet: Boolean,
    val dryingHours: Int? = null,
    /** Texto listo para la sublínea del hero ("Seca en ~12 h"). */
    val message: String? = null
)

@Serializable
data class HourForecastDto(
    val time: String,
    val temperature: Double,
    val humidity: Double,
    val windSpeed: Double,
    val precipitation: Double,
    val precipitationProbability: Int,
    val cloudCover: Int,
    val dewPoint: Double? = null,
    val score: Int,
    val scoreLabel: String,
    val weatherCode: Int = 0
)

@Serializable
data class DayForecastDto(
    val date: String,
    val tempMax: Double,
    val tempMin: Double,
    val precipitationTotal: Double,
    val avgScore: Int,
    val scoreLabel: String
)

@Serializable
data class BestDayDto(
    val date: String,
    val score: Int,
    val label: String,
    val daysFromToday: Int
)

@Serializable
data class OptimalWindowDto(
    val start: String,
    val end: String,
    val avgScore: Int
)

@Serializable
data class ScoreFactorDto(
    val name: String,
    val display: String,
    val passes: Boolean
)

@Serializable
data class SchoolScoreDto(
    val id: String,
    val todayScore: Int,
    val hourlyScores: List<Int>,
    val dryRock: Boolean,
    val rainMm: Double = 0.0,
    val rainProb: Int = 0
)

@Serializable
data class RangeScoreDto(
    val id: String,
    val combinedScore: Int = 0,
    val avgScore: Int = 0,
    val days: List<RangeDayScoreDto> = emptyList(),
    val rainDays: Int = 0,
    val maxRainMm: Double = 0.0
)

@Serializable
data class RangeDayScoreDto(
    val date: String,
    val score: Int = 0,
    val rainMm: Double = 0.0,
    val rainProb: Int = 0,
    val rainy: Boolean = false
)
