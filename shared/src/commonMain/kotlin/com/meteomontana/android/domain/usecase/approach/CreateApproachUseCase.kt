package com.meteomontana.android.domain.usecase.approach

import com.meteomontana.android.data.api.dto.CreateApproachRequest
import com.meteomontana.android.domain.model.Approach
import com.meteomontana.android.domain.repository.ApproachRepository

/** SOLO ADMIN — el backend responde 403 a cualquier otro uid. */
class CreateApproachUseCase(private val repo: ApproachRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(schoolId: String, req: CreateApproachRequest): Approach =
        repo.createApproach(schoolId, req)
}
