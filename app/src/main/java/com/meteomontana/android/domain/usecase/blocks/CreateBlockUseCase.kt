package com.meteomontana.android.domain.usecase.blocks

import com.meteomontana.android.data.api.BlockApi
import com.meteomontana.android.data.api.dto.CreateBlockRequest
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.Block
import javax.inject.Inject

class CreateBlockUseCase @Inject constructor(private val api: BlockApi) {
    suspend operator fun invoke(schoolId: String, req: CreateBlockRequest): Block =
        api.createBlock(schoolId, req).toDomain()
}
