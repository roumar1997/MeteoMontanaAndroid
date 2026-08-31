package com.meteomontana.android.domain.usecase.approach

import com.meteomontana.android.domain.model.Approach
import com.meteomontana.android.domain.repository.ApproachRepository

class GetApproachesUseCase(private val repo: ApproachRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(schoolId: String): List<Approach> = repo.getApproaches(schoolId)
}
