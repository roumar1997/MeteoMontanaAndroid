package com.meteomontana.android.domain.repository

import com.meteomontana.android.data.api.dto.ContributionRequest
import com.meteomontana.android.domain.model.Contribution

interface ContributionRepository {
    suspend fun submitContribution(schoolId: String, req: ContributionRequest): Contribution
    suspend fun getMyContributions(): List<Contribution>
}
