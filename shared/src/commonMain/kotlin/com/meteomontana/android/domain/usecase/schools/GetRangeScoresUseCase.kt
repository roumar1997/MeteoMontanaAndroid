package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.domain.model.RangeScore
import com.meteomontana.android.domain.repository.ForecastRepository

/**
 * Score de un tramo de días elegidos para varias escuelas. Lo usa el selector
 * de días de la lista: media de días penalizada por la lluvia, con detalle de
 * qué días llueve. Cálculo en el backend (cacheado); la app encadena por lotes.
 */
class GetRangeScoresUseCase(private val repository: ForecastRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(ids: List<String>, dates: List<String>): List<RangeScore> =
        repository.getRangeScores(ids, dates)
}
