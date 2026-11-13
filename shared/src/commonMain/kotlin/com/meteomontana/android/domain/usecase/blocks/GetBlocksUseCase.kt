package com.meteomontana.android.domain.usecase.blocks

import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.repository.BlockRepository

class GetBlocksUseCase(private val repository: BlockRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(schoolId: String): List<Block> = repository.getBlocks(schoolId)

    /** Última página buena guardada en el móvil (null si nunca se cargó): para pintar
     *  el mapa al momento mientras llega la red (stale-while-revalidate). No lanza. */
    fun cached(schoolId: String): List<Block>? = repository.getCachedBlocks(schoolId)
}
