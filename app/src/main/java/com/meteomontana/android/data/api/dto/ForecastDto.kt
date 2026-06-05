package com.meteomontana.android.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Respuesta de GET /api/schools/{id}/forecast. */
@JsonClass(generateAdapter = true)
data class ForecastDto(
    @Json(name = "schoolId")   val schoolId: String,
    @Json(name = "schoolName") val schoolName: String,
    @Json(name = "lat")        val lat: Double,
    @Json(name = "lon")        val lon: Double,
    @Json(name = "current")    val current: CurrentDto,
    @Json(name = "hours")      val hours: List<HourForecastDto>,
    @Json(name = "days")       val days: List<DayForecastDto>,
    @Json(name = "bestDay")    val bestDay: BestDayDto?,
    @Json(name = "bestWindow") val bestWindow: OptimalWindowDto?
)

@JsonClass(generateAdapter = true)
data class CurrentDto(
    val time: String,
    val temperature: Double,
    val humidity: Double,
    val windSpeed: Double,
    val precipitation: Double,
    val precipitationProbability: Int,
    val cloudCover: Int,
    val dewPoint: Double?,
    val precip24h: Double,
    val precip72h: Double,
    val dryRock: Boolean,
    val score: Int,
    val scoreLabel: String,
    val factors: List<ScoreFactorDto>
)

@JsonClass(generateAdapter = true)
data class HourForecastDto(
    val time: String,
    val temperature: Double,
    val humidity: Double,
    val windSpeed: Double,
    val precipitation: Double,
    val precipitationProbability: Int,
    val cloudCover: Int,
    val dewPoint: Double?,
    val score: Int,
    val scoreLabel: String,
    val weatherCode: Int = 0    // código WMO de Open-Meteo para el icono
)

@JsonClass(generateAdapter = true)
data class DayForecastDto(
    val date: String,
    val tempMax: Double,
    val tempMin: Double,
    val precipitationTotal: Double,
    val avgScore: Int,
    val scoreLabel: String
)

@JsonClass(generateAdapter = true)
data class BestDayDto(
    val date: String,
    val score: Int,
    val label: String,
    val daysFromToday: Int
)

@JsonClass(generateAdapter = true)
data class OptimalWindowDto(
    val start: String,
    val end: String,
    val avgScore: Int
)

@JsonClass(generateAdapter = true)
data class ScoreFactorDto(
    val name: String,
    val display: String,
    val passes: Boolean
)
