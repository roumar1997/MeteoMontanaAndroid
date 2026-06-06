package com.meteomontana.android.data.api.dto

import com.meteomontana.android.domain.model.BestDay
import com.meteomontana.android.domain.model.Current
import com.meteomontana.android.domain.model.DayForecast
import com.meteomontana.android.domain.model.Forecast
import com.meteomontana.android.domain.model.HourForecast
import com.meteomontana.android.domain.model.OptimalWindow
import com.meteomontana.android.domain.model.ScoreFactor

fun ForecastDto.toDomain() = Forecast(
    schoolId = schoolId, schoolName = schoolName, lat = lat, lon = lon,
    current = current.toDomain(),
    hours = hours.map { it.toDomain() },
    days = days.map { it.toDomain() },
    bestDay = bestDay?.toDomain(),
    bestWindow = bestWindow?.toDomain()
)

fun CurrentDto.toDomain() = Current(
    time, temperature, humidity, windSpeed, precipitation,
    precipitationProbability, cloudCover, dewPoint, precip24h, precip72h,
    dryRock, score, scoreLabel, factors.map { it.toDomain() }
)

fun HourForecastDto.toDomain() = HourForecast(
    time, temperature, humidity, windSpeed, precipitation,
    precipitationProbability, cloudCover, dewPoint, score, scoreLabel, weatherCode
)

fun DayForecastDto.toDomain() = DayForecast(
    date, tempMax, tempMin, precipitationTotal, avgScore, scoreLabel
)

fun BestDayDto.toDomain() = BestDay(date, score, label, daysFromToday)

fun OptimalWindowDto.toDomain() = OptimalWindow(start, end, avgScore)

fun ScoreFactorDto.toDomain() = ScoreFactor(name, display, passes)
