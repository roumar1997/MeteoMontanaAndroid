package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.domain.model.SchoolRainOutlook
import com.meteomontana.android.domain.repository.ForecastRepository

/**
 * Lluvia y nota hora a hora (próximas 48 h) de varias escuelas: alimenta el modo «Previsión» del Radar. Cálculo en el
 * backend (cacheado por lista y hora, máx. 50 escuelas por llamada); la app encadena por lotes.
 */
class GetRainOutlookUseCase(private val repository: ForecastRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(ids: List<String>): List<SchoolRainOutlook> =
        repository.getRainOutlook(ids)
}
