package com.meteomontana.android.domain.usecase.contributions

import com.meteomontana.android.data.api.dto.ContributionRequest
import com.meteomontana.android.domain.model.Contribution
import com.meteomontana.android.domain.repository.ContributionRepository

/**
 * Cómo acabó el ÚLTIMO envío: [publishedDirectly] = el servidor ya la publicó (admin o persona de confianza), así que
 * la pantalla de éxito no debe decir "la revisaremos en 24-48 h". Se lee justo al enseñar esa pantalla, que sale
 * inmediatamente después del envío; un envío sin red (cola) o fallido lo deja en false.
 */
object ContributionOutcome {
    var publishedDirectly: Boolean = false
}

class SubmitContributionUseCase(private val repo: ContributionRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(schoolId: String, req: ContributionRequest): Contribution {
        ContributionOutcome.publishedDirectly = false
        val sent = repo.submitContribution(schoolId, req)
        ContributionOutcome.publishedDirectly = sent.status.equals("APPROVED", ignoreCase = true)
        return sent
    }
}
