package com.meteomontana.android.domain.usecase.blocks

import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.repository.BlockRepository

class GetBlockUseCase(private val repo: BlockRepository) {
    suspend operator fun invoke(blockId: String): Block = repo.getBlock(blockId)
}
