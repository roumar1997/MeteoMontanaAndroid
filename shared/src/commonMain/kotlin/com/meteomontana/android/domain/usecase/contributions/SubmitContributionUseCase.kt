package com.meteomontana.android.domain.usecase.contributions

import com.meteomontana.android.data.api.dto.ContributionRequest
import com.meteomontana.android.domain.model.Contribution
import com.meteomontana.android.domain.repository.ContributionRepository

class SubmitContributionUseCase(private val repo: ContributionRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(schoolId: String, req: ContributionRequest): Contribution =
        repo.submitContribution(schoolId, req)
}
