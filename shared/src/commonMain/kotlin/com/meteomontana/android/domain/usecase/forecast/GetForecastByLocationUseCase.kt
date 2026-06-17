package com.meteomontana.android.domain.usecase.forecast

import com.meteomontana.android.domain.model.Forecast
import com.meteomontana.android.domain.repository.ForecastRepository

class GetForecastByLocationUseCase(private val repository: ForecastRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(lat: Double, lon: Double, schoolId: String? = null): Forecast =
        repository.getForecastByLocation(lat, lon, schoolId)
}
