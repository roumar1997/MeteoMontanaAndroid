package com.meteomontana.android.domain.usecase.blocks

import com.meteomontana.android.data.api.BlockApi
import com.meteomontana.android.data.api.dto.CreateBlockRequest
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.Block
import javax.inject.Inject

class UpdateBlockUseCase @Inject constructor(private val api: BlockApi) {
    suspend operator fun invoke(blockId: String, req: CreateBlockRequest): Block =
        api.updateBlock(blockId, req).toDomain()
}
