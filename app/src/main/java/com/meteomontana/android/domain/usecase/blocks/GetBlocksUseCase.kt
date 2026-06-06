package com.meteomontana.android.domain.usecase.blocks

import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.Block
import javax.inject.Inject

class GetBlocksUseCase @Inject constructor(private val api: SchoolApi) {
    suspend operator fun invoke(schoolId: String): List<Block> =
        api.getBlocks(schoolId).map { it.toDomain() }
}
