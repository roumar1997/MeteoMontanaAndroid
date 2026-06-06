package com.meteomontana.android.domain.usecase.blocks

import com.meteomontana.android.data.api.SchoolApi
import javax.inject.Inject

class DeleteBlockUseCase @Inject constructor(private val api: SchoolApi) {
    suspend operator fun invoke(blockId: String) = api.deleteBlock(blockId)
}
