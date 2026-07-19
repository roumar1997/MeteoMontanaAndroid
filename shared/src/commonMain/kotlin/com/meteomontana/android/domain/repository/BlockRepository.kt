package com.meteomontana.android.domain.repository

import com.meteomontana.android.data.api.dto.CreateBlockRequest
import com.meteomontana.android.domain.model.Block

interface BlockRepository {
    suspend fun getBlocks(schoolId: String): List<Block>

    /** Última página buena cacheada en disco de [schoolId] (stale-while-
     *  revalidate del detalle), o null si no hay caché disponible. */
    fun getCachedBlocks(schoolId: String): List<Block>? = null
    suspend fun getBlock(blockId: String): Block
    suspend fun createBlock(schoolId: String, req: CreateBlockRequest): Block
    suspend fun updateBlock(blockId: String, req: CreateBlockRequest): Block
    suspend fun deleteBlock(blockId: String)
}
