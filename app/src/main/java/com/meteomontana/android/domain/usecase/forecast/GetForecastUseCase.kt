package com.meteomontana.android.domain.usecase.forecast

import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.dto.ForecastDto
import javax.inject.Inject

/**
 * Pide el forecast (tiempo + score por hora) de una escuela.
 * Por ahora envuelve `SchoolApi.getForecast`; en Fase 1.3 vivirá detrás
 * de `ForecastApi` cuando partamos el god-interface por bounded context.
 */
class GetForecastUseCase @Inject constructor(
    private val api: SchoolApi
) {
    suspend operator fun invoke(schoolId: String): ForecastDto = api.getForecast(schoolId)
}
