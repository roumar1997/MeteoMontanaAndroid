package com.meteomontana.android.domain.usecase.forecast

import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.Forecast
import javax.inject.Inject

class GetForecastUseCase @Inject constructor(private val api: SchoolApi) {
    suspend operator fun invoke(schoolId: String): Forecast = api.getForecast(schoolId).toDomain()
}
