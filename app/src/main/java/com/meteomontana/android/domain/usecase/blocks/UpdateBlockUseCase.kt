package com.meteomontana.android.domain.usecase.blocks

import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.dto.BlockDto
import com.meteomontana.android.data.api.dto.CreateBlockRequest
import javax.inject.Inject

class UpdateBlockUseCase @Inject constructor(private val api: SchoolApi) {
    suspend operator fun invoke(blockId: String, req: CreateBlockRequest): BlockDto =
        api.updateBlock(blockId, req)
}
