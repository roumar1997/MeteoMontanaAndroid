package com.meteomontana.android.domain.usecase.forecast

import com.meteomontana.android.domain.model.Forecast
import com.meteomontana.android.domain.repository.ForecastRepository

class GetForecastUseCase(private val repository: ForecastRepository) {
    suspend operator fun invoke(schoolId: String): Forecast = repository.getForecast(schoolId)
}
