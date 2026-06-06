package com.meteomontana.android.domain.usecase.blocks

import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.dto.BlockDto
import com.meteomontana.android.data.api.dto.CreateBlockRequest
import javax.inject.Inject

class CreateBlockUseCase @Inject constructor(private val api: SchoolApi) {
    suspend operator fun invoke(schoolId: String, req: CreateBlockRequest): BlockDto =
        api.createBlock(schoolId, req)
}
