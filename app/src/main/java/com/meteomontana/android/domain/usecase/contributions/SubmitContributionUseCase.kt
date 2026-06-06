package com.meteomontana.android.domain.usecase.contributions

import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.dto.ContributionDto
import com.meteomontana.android.data.api.dto.ContributionRequest
import javax.inject.Inject

class SubmitContributionUseCase @Inject constructor(private val api: SchoolApi) {
    suspend operator fun invoke(schoolId: String, req: ContributionRequest): ContributionDto =
        api.submitContribution(schoolId, req)
}
