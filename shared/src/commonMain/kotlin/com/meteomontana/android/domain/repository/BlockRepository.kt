package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.Block

interface BlockRepository {
    suspend fun getBlocks(schoolId: String): List<Block>
    suspend fun deleteBlock(blockId: String)
}
