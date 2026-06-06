package com.meteomontana.android.domain.usecase.blocks

import com.meteomontana.android.data.api.dto.CreateBlockRequest
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.repository.BlockRepository

class CreateBlockUseCase(private val repo: BlockRepository) {
    suspend operator fun invoke(schoolId: String, req: CreateBlockRequest): Block =
        repo.createBlock(schoolId, req)
}
