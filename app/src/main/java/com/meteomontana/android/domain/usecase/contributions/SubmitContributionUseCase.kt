package com.meteomontana.android.domain.usecase.contributions

import com.meteomontana.android.data.api.ContributionApi
import com.meteomontana.android.data.api.dto.ContributionRequest
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.Contribution
import javax.inject.Inject

class SubmitContributionUseCase @Inject constructor(private val api: ContributionApi) {
    suspend operator fun invoke(schoolId: String, req: ContributionRequest): Contribution =
        api.submitContribution(schoolId, req).toDomain()
}
