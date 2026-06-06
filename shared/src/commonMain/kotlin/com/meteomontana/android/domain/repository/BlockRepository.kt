package com.meteomontana.android.domain.repository

import com.meteomontana.android.data.api.dto.CreateBlockRequest
import com.meteomontana.android.domain.model.Block

interface BlockRepository {
    suspend fun getBlocks(schoolId: String): List<Block>
    suspend fun getBlock(blockId: String): Block
    suspend fun createBlock(schoolId: String, req: CreateBlockRequest): Block
    suspend fun updateBlock(blockId: String, req: CreateBlockRequest): Block
    suspend fun deleteBlock(blockId: String)
}
