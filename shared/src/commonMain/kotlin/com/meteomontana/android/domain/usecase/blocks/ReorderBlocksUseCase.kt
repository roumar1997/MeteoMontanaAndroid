package com.meteomontana.android.domain.usecase.blocks

import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.repository.BlockRepository

/** Renumera las piedras de un sector (o las sin sector) según el orden
 *  exacto que se le pase. Solo admin (Álvaro, 2026-09-14: "que se pueda
 *  arreglar cuando una piedra se añade fuera de orden respecto al camino"). */
class ReorderBlocksUseCase(private val repo: BlockRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(schoolId: String, sectorBlockId: String?, orderedBlockIds: List<String>): List<Block> =
        repo.reorderBlocks(schoolId, sectorBlockId, orderedBlockIds)
}

/** Sugiere y aplica un primer orden por distancia al parking más cercano —
 *  punto de partida a corregir a mano con [ReorderBlocksUseCase] si el
 *  camino real no va en línea recta. */
class AutoReorderBlocksUseCase(private val repo: BlockRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(schoolId: String, sectorBlockId: String?): List<Block> =
        repo.autoReorderBlocks(schoolId, sectorBlockId)
}
