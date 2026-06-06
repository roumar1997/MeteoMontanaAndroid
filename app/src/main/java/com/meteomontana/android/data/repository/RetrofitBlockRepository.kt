package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.BlockApi
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.repository.BlockRepository
import javax.inject.Inject

class RetrofitBlockRepository @Inject constructor(
    private val api: BlockApi
) : BlockRepository {
    override suspend fun getBlocks(schoolId: String): List<Block> =
        api.getBlocks(schoolId).map { it.toDomain() }
    override suspend fun deleteBlock(blockId: String) = api.deleteBlock(blockId)
}
