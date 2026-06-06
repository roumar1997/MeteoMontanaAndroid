package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.Forecast
import com.meteomontana.android.domain.model.SchoolScore

interface ForecastRepository {
    suspend fun getForecast(schoolId: String): Forecast
    suspend fun getForecastByLocation(lat: Double, lon: Double, schoolId: String? = null): Forecast
    suspend fun getTodayScores(ids: List<String>): List<SchoolScore>
}
