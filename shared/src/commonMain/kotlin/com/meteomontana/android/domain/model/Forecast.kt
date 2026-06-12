package com.meteomontana.android.domain.model

data class Forecast(
    val schoolId: String,
    val schoolName: String,
    val lat: Double,
    val lon: Double,
    val current: Current,
    val hours: List<HourForecast>,
    val days: List<DayForecast>,
    val bestDay: BestDay?,
    val bestWindow: OptimalWindow?
)

data class Current(
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
    /** Horas hasta roca seca. 0 = seca ya; null = >7 días o backend antiguo. */
    val hoursToDry: Int? = null,
    val score: Int,
    val scoreLabel: String,
    val factors: List<ScoreFactor>
)

data class HourForecast(
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
    val weatherCode: Int = 0
)

data class DayForecast(
    val date: String,
    val tempMax: Double,
    val tempMin: Double,
    val precipitationTotal: Double,
    val avgScore: Int,
    val scoreLabel: String
)

data class BestDay(val date: String, val score: Int, val label: String, val daysFromToday: Int)

data class OptimalWindow(val start: String, val end: String, val avgScore: Int)

data class ScoreFactor(val name: String, val display: String, val passes: Boolean)
