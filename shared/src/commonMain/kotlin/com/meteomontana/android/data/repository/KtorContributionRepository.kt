package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorContributionApi
import com.meteomontana.android.data.api.dto.ContributionRequest
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.Contribution
import com.meteomontana.android.domain.repository.ContributionRepository

class KtorContributionRepository(private val api: KtorContributionApi) : ContributionRepository {

    override suspend fun submitContribution(schoolId: String, req: ContributionRequest): Contribution =
        api.submitContribution(schoolId, req).toDomain()

    override suspend fun getMyContributions(): List<Contribution> =
        api.getMyContributions().map { it.toDomain() }
}
