package com.meteomontana.android.domain.usecase.blocks

import com.meteomontana.android.domain.repository.BlockRepository

class DeleteBlockUseCase(private val repository: BlockRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(blockId: String) = repository.deleteBlock(blockId)
}
