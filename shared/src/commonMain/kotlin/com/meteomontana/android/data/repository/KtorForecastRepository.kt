package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorForecastApi
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.Forecast
import com.meteomontana.android.domain.model.SchoolScore
import com.meteomontana.android.domain.repository.ForecastRepository

class KtorForecastRepository(private val api: KtorForecastApi) : ForecastRepository {

    override suspend fun getForecast(schoolId: String): Forecast =
        api.getForecast(schoolId).toDomain()

    override suspend fun getTodayScores(ids: List<String>): List<SchoolScore> =
        api.getTodayScores(ids).map { it.toDomain() }
}
