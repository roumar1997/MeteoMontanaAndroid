package com.meteomontana.android.domain.usecase.approach

import com.meteomontana.android.domain.repository.ApproachRepository

/** SOLO ADMIN. Borra el camino y sus chinchetas (cascada en el backend). */
class DeleteApproachUseCase(private val repo: ApproachRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(approachId: String) = repo.deleteApproach(approachId)
}
