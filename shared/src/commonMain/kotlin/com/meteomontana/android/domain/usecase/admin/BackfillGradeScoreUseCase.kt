package com.meteomontana.android.domain.usecase.admin

import com.meteomontana.android.domain.model.GradeScoreBackfillResult
import com.meteomontana.android.domain.repository.AdminRepository

class BackfillGradeScoreUseCase(private val repository: AdminRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): GradeScoreBackfillResult = repository.backfillGradeScore()
}
