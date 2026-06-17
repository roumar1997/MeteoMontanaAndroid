package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.ForecastDto
import com.meteomontana.android.data.api.dto.RangeScoreDto
import com.meteomontana.android.data.api.dto.SchoolScoreDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class KtorForecastApi(private val client: HttpClient) {

    suspend fun getForecast(schoolId: String): ForecastDto =
        client.get("schools/$schoolId/forecast").body()

    suspend fun getTodayScores(ids: List<String>): List<SchoolScoreDto> =
        client.get("forecast/today-scores") {
            ids.forEach { parameter("ids", it) }
        }.body()

    suspend fun getRangeScores(ids: List<String>, dates: List<String>): List<RangeScoreDto> =
        client.get("forecast/range-scores") {
            ids.forEach { parameter("ids", it) }
            dates.forEach { parameter("dates", it) }
        }.body()

    suspend fun getForecastByLocation(lat: Double, lon: Double, schoolId: String?): ForecastDto =
        client.get("forecast/by-location") {
            parameter("lat", lat)
            parameter("lon", lon)
            schoolId?.let { parameter("schoolId", it) }
        }.body()
}
