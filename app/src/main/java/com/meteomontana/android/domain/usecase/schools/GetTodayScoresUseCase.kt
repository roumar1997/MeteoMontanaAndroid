package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.dto.SchoolScoreDto
import javax.inject.Inject

/**
 * Obtiene los scores del día para una lista de escuelas (batch hasta 50).
 * Por ahora envuelve `SchoolApi.getTodayScores` directamente; en Fase 1.3
 * se moverá a una `ScoreRepository`/`ScoreApi` propios cuando partamos el
 * god-interface por bounded context.
 */
class GetTodayScoresUseCase @Inject constructor(
    private val api: SchoolApi
) {
    suspend operator fun invoke(ids: List<String>): List<SchoolScoreDto> =
        if (ids.isEmpty()) emptyList() else api.getTodayScores(ids)
}
