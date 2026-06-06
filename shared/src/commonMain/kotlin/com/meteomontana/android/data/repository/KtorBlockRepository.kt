package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorBlockApi
import com.meteomontana.android.data.api.dto.CreateBlockRequest
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.repository.BlockRepository

class KtorBlockRepository(private val api: KtorBlockApi) : BlockRepository {

    override suspend fun getBlocks(schoolId: String): List<Block> =
        api.getBlocks(schoolId).map { it.toDomain() }

    override suspend fun getBlock(blockId: String): Block =
        api.getBlock(blockId).toDomain()

    override suspend fun createBlock(schoolId: String, req: CreateBlockRequest): Block =
        api.createBlock(schoolId, req).toDomain()

    override suspend fun updateBlock(blockId: String, req: CreateBlockRequest): Block =
        api.updateBlock(blockId, req).toDomain()

    override suspend fun deleteBlock(blockId: String) {
        api.deleteBlock(blockId)
    }
}
