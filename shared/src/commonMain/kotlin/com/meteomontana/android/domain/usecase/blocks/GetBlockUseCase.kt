package com.meteomontana.android.domain.usecase.blocks

import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.repository.BlockRepository

/** Un bloque suelto por id — para saltar desde el Historial de admin directo
 *  a "MODIFICAR" sin tener que cargar toda la escuela (Álvaro, 2026-09-30). */
class GetBlockUseCase(private val repository: BlockRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(blockId: String): Block = repository.getBlock(blockId)
}
