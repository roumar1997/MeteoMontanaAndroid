package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.domain.model.SchoolScore
import com.meteomontana.android.domain.repository.ForecastRepository

class GetTodayScoresUseCase(private val repository: ForecastRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(ids: List<String>): List<SchoolScore> =
        repository.getTodayScores(ids)
}
