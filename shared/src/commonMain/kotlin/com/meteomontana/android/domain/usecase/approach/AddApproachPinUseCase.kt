package com.meteomontana.android.domain.usecase.approach

import com.meteomontana.android.data.api.dto.AddApproachPinRequest
import com.meteomontana.android.domain.model.ApproachPin
import com.meteomontana.android.domain.repository.ApproachRepository

class AddApproachPinUseCase(private val repo: ApproachRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(approachId: String, req: AddApproachPinRequest): ApproachPin =
        repo.addPin(approachId, req)
}
