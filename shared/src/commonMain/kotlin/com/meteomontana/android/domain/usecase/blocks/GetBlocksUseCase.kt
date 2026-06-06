package com.meteomontana.android.domain.usecase.blocks

import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.repository.BlockRepository

class GetBlocksUseCase(private val repository: BlockRepository) {
    suspend operator fun invoke(schoolId: String): List<Block> = repository.getBlocks(schoolId)
}
