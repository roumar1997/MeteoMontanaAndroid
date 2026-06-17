package com.meteomontana.android.domain.usecase.journal

import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.JournalSession
import com.meteomontana.android.domain.repository.BlockRepository

/** Nº de piedra y sector de una vía del diario, resueltos en vivo del catálogo. */
data class ViaCatalogInfo(val boulderNumber: String?, val sector: String?)

/**
 * Para cada entrada del diario resuelve el **número de piedra** y el **sector**
 * actuales desde el catálogo de bloques de su escuela.
 *
 * No se guardan en la propia entrada (el catálogo se recicla: las piedras se
 * renumeran y los sectores cambian), así que se resuelven al mostrar la lista.
 * `Block.name` de una piedra (type BLOCK) es su número; `sectorBlockId` apunta a
 * la ZONA (sector) a la que pertenece. La vía se localiza por nombre dentro de
 * las `lines` de la piedra.
 *
 * Devuelve `entryId -> info`. Si una escuela no se puede cargar (sin red) o la vía
 * no aparece en el catálogo, esa entrada simplemente no lleva info.
 */
class GetJournalViaInfoUseCase(private val blockRepository: BlockRepository) {
    suspend operator fun invoke(entries: List<JournalSession>): Map<String, ViaCatalogInfo> {
        val result = mutableMapOf<String, ViaCatalogInfo>()
        val schoolIds = entries.mapNotNull { it.schoolId?.takeIf { id -> id.isNotBlank() } }.toSet()
        val blocksBySchool = mutableMapOf<String, List<Block>>()
        for (sid in schoolIds) {
            blocksBySchool[sid] = runCatching { blockRepository.getBlocks(sid) }.getOrDefault(emptyList())
        }
        for (e in entries) {
            val sid = e.schoolId?.takeIf { it.isNotBlank() } ?: continue
            val blocks = blocksBySchool[sid] ?: continue
            if (blocks.isEmpty()) continue
            val zonesById = blocks.filter { it.type == "ZONE" }.associateBy { it.id }
            val via = e.blockName.trim()
            // Piedras cuya lista de vías contiene esta vía por nombre.
            val candidates = blocks.filter { b ->
                b.type == "BLOCK" && b.lines.any { it.name.trim().equals(via, ignoreCase = true) }
            }
            if (candidates.isEmpty()) continue
            // Si la entrada recuerda su sector, prefiero la piedra de ese sector.
            val rememberedSector = e.sector?.trim()?.takeIf { it.isNotEmpty() }
            val chosen = rememberedSector?.let { sectorName ->
                candidates.firstOrNull { b ->
                    val z = b.sectorBlockId?.let { zonesById[it] }
                    z?.name?.trim()?.equals(sectorName, ignoreCase = true) == true
                }
            } ?: candidates.first()
            val sectorName = chosen.sectorBlockId?.let { zonesById[it]?.name }
            result[e.id] = ViaCatalogInfo(
                boulderNumber = chosen.name.takeIf { it.isNotBlank() },
                sector = sectorName?.takeIf { it.isNotBlank() }
            )
        }
        return result
    }
}
