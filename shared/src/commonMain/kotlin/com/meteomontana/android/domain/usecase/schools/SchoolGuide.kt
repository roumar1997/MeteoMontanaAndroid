package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.domain.model.LineSearchHit
import kotlin.math.PI
import kotlin.math.cos

/** Una piedra de la guía con todas sus vías (en el orden en que se dibujaron/nombraron). */
data class GuideBlock(val blockId: String, val blockName: String, val lines: List<LineSearchHit>) {
    /** Foto de la piedra: la de su primera vía con foto. */
    val photoPath: String? get() = lines.firstOrNull { !it.photoPath.isNullOrEmpty() }?.photoPath
}

/** Un sector de la guía: sus piedras en el orden en que te las encuentras andando. */
data class GuideSector(val sector: String?, val blocks: List<GuideBlock>) {
    val lineCount: Int get() = blocks.sumOf { it.lines.size }
}

/**
 * Modo GUÍA de la pestaña BLOQUES: la escuela como una guía de papel, sector a sector y piedra a piedra.
 * Lógica PURA (sin red ni plataforma), compartida por iOS y Android.
 *
 * Orden de las piedras dentro de un sector: no hay orden "oficial" guardado, así que se calcula con sus
 * coordenadas a lo largo del eje en que el sector es más largo (oeste→este o norte→sur). Es lo que hace una
 * guía impresa: recorrer el sector de un extremo al otro. Las piedras sin coordenadas van al final por nombre.
 */
object SchoolGuide {

    fun sectors(lines: List<LineSearchHit>): List<GuideSector> {
        val withLine = lines.filter { it.lineId != null }
        val bySector = LinkedHashMap<String?, MutableList<LineSearchHit>>()
        for (h in withLine.sortedBy { SchoolLinesExplorer.fold(it.sectorName ?: "￿") }) {
            bySector.getOrPut(h.sectorName?.takeIf { it.isNotBlank() }) { mutableListOf() }.add(h)
        }
        return bySector.map { (sector, hits) -> GuideSector(sector, orderBlocks(blocks(hits))) }
    }

    private fun blocks(hits: List<LineSearchHit>): List<GuideBlock> {
        val byBlock = LinkedHashMap<String, MutableList<LineSearchHit>>()
        for (h in hits) byBlock.getOrPut(h.blockId) { mutableListOf() }.add(h)
        return byBlock.map { (id, list) ->
            GuideBlock(id, list.first().blockName, list.sortedWith(compareBy({ it.lineName ?: "" }, { it.lineId ?: "" })))
        }
    }

    /** Ordena por el eje más largo del sector; sin coordenadas, al final y por nombre. */
    fun orderBlocks(blocks: List<GuideBlock>): List<GuideBlock> {
        val placed = blocks.mapNotNull { b -> position(b)?.let { b to it } }
        val loose = blocks.filter { position(it) == null }.sortedBy { SchoolLinesExplorer.fold(it.blockName) }
        if (placed.isEmpty()) return loose
        val meanLat = placed.map { it.second.first }.average()
        val k = cos(meanLat * PI / 180)   // un grado de longitud mide menos que uno de latitud
        val xs = placed.map { it.second.second * k }
        val ys = placed.map { it.second.first }
        val eastWest = (xs.max() - xs.min()) >= (ys.max() - ys.min())
        val sorted = placed.sortedWith(compareBy(
            { if (eastWest) it.second.second else -it.second.first },   // oeste→este o norte→sur
            { SchoolLinesExplorer.fold(it.first.blockName) }
        )).map { it.first }
        return sorted + loose
    }

    private fun position(b: GuideBlock): Pair<Double, Double>? {
        val h = b.lines.firstOrNull { it.lat != null && it.lon != null } ?: return null
        return h.lat!! to h.lon!!
    }
}
