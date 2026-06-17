package com.meteomontana.android.domain.usecase.blocks

import com.meteomontana.android.data.api.dto.CreateBlockRequest
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.repository.BlockRepository

class UpdateBlockUseCase(private val repo: BlockRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(blockId: String, req: CreateBlockRequest): Block =
        repo.updateBlock(blockId, req)
}
