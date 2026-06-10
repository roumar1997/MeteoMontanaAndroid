package com.meteomontana.android.domain.usecase.contributions

import com.meteomontana.android.domain.model.Contribution
import com.meteomontana.android.domain.repository.ContributionRepository

class GetMyContributionsUseCase(private val repo: ContributionRepository) {
    suspend operator fun invoke(): List<Contribution> = repo.getMyContributions()
}
