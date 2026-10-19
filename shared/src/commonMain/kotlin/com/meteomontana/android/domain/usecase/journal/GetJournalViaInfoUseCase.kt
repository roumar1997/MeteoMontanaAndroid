package com.meteomontana.android.domain.usecase.journal

import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.BlockLine
import com.meteomontana.android.domain.model.JournalSession
import com.meteomontana.android.domain.repository.BlockRepository

/** Nº de piedra, sector y grado ACTUAL de una vía del diario, resueltos en vivo
 *  del catálogo (el grado puede haber cambiado tras una corrección). */
data class ViaCatalogInfo(
    val boulderNumber: String?,
    val sector: String?,
    val grade: String? = null,
    // true = la entrada tenía un lineId estable que YA NO existe en el catálogo
    // (la vía/piedra se borró). La UI la pinta en gris como "vía eliminada", pero
    // NO se elimina del perfil (no se castiga al usuario por un borrado de catálogo).
    val deleted: Boolean = false,
    // Foto de la cara donde está la vía y su trazo (JSON de puntos), para la miniatura de las listas del diario
    // (Álvaro, 2026-10-19). null si la piedra no tiene foto o la vía no está dibujada.
    val photoUrl: String? = null,
    val linePath: String? = null
)

/** La foto de la cara donde está [line] (o la de la piedra) y el trazo de esa vía. */
internal fun thumbnailOf(block: Block, line: BlockLine?): Pair<String?, String?> {
    val face = line?.let { l -> block.faces.firstOrNull { f -> f.lines.any { it.id == l.id } } }
    val photo = (face?.photoPath ?: block.photoPath ?: block.faces.firstOrNull()?.photoPath)?.takeIf { it.isNotBlank() }
    return photo to line?.linePath?.takeIf { it.isNotBlank() }
}

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

            // 1) Enganche EXACTO por lineId estable (preferente): aguanta renombres,
            //    nombres duplicados y reordenes. Devuelve la piedra/sector/grado EN VIVO.
            val byId = e.lineId?.takeIf { it.isNotBlank() }?.let { lid ->
                blocks.firstOrNull { b -> b.type == "BLOCK" && b.lines.any { it.id == lid } }
            }
            if (byId != null) {
                val sectorName = byId.sectorBlockId?.let { zonesById[it]?.name }
                val liveLine = byId.lines.firstOrNull { it.id == e.lineId }
                val liveGrade = liveLine?.grade?.takeIf { it.isNotBlank() }
                val (photo, path) = thumbnailOf(byId, liveLine)
                result[e.id] = ViaCatalogInfo(
                    boulderNumber = byId.name.takeIf { it.isNotBlank() },
                    sector = sectorName?.takeIf { it.isNotBlank() },
                    grade = liveGrade,
                    photoUrl = photo,
                    linePath = path
                )
                continue
            }

            // 2) Fallback por NOMBRE (entradas antiguas/offline sin lineId).
            // Piedras cuya lista de vías contiene esta vía por nombre.
            val candidates = blocks.filter { b ->
                b.type == "BLOCK" && b.lines.any { it.name.trim().equals(via, ignoreCase = true) }
            }
            if (candidates.isEmpty()) {
                // Si la entrada tenía un lineId estable y el catálogo (cargado) ya
                // no lo contiene ni por id ni por nombre → la vía se BORRÓ. La
                // marcamos como eliminada (la UI la pinta en gris). Para entradas
                // antiguas SIN lineId no asumimos borrado (el nombre puede variar).
                if (!e.lineId.isNullOrBlank()) {
                    result[e.id] = ViaCatalogInfo(null, null, deleted = true)
                }
                continue
            }
            // Si la entrada recuerda su sector, prefiero la piedra de ese sector.
            val rememberedSector = e.sector?.trim()?.takeIf { it.isNotEmpty() }
            val chosen = rememberedSector?.let { sectorName ->
                candidates.firstOrNull { b ->
                    val z = b.sectorBlockId?.let { zonesById[it] }
                    z?.name?.trim()?.equals(sectorName, ignoreCase = true) == true
                }
            } ?: candidates.first()
            val sectorName = chosen.sectorBlockId?.let { zonesById[it]?.name }
            val chosenLine = chosen.lines.firstOrNull { it.name.trim().equals(via, ignoreCase = true) }
            val currentGrade = chosenLine?.grade?.takeIf { it.isNotBlank() }
            val (photo, path) = thumbnailOf(chosen, chosenLine)
            result[e.id] = ViaCatalogInfo(
                boulderNumber = chosen.name.takeIf { it.isNotBlank() },
                sector = sectorName?.takeIf { it.isNotBlank() },
                grade = currentGrade,
                photoUrl = photo,
                linePath = path
            )
        }
        return result
    }
}
