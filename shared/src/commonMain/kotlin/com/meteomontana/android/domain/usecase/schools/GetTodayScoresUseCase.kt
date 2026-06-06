package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.domain.model.SchoolScore
import com.meteomontana.android.domain.repository.ForecastRepository

class GetTodayScoresUseCase(private val repository: ForecastRepository) {
    suspend operator fun invoke(ids: List<String>): List<SchoolScore> =
        repository.getTodayScores(ids)
}
