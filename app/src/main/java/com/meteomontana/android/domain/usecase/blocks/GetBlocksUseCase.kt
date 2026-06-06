package com.meteomontana.android.domain.usecase.blocks

import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.dto.BlockDto
import javax.inject.Inject

class GetBlocksUseCase @Inject constructor(private val api: SchoolApi) {
    suspend operator fun invoke(schoolId: String): List<BlockDto> = api.getBlocks(schoolId)
}
