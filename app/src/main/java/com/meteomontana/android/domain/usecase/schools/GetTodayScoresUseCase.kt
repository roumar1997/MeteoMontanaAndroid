package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.SchoolScore
import javax.inject.Inject

class GetTodayScoresUseCase @Inject constructor(
    private val api: SchoolApi
) {
    suspend operator fun invoke(ids: List<String>): List<SchoolScore> =
        if (ids.isEmpty()) emptyList() else api.getTodayScores(ids).map { it.toDomain() }
}
