package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.LineSearchHit

/**
 * Pasa las piedras de una escuela (las que la app guarda para usar sin conexión) a las vías que pinta la guía.
 *
 * - [fromBlocks]: SIN conexión la guía sale de aquí, entera y con sus fotos (si se descargaron). No trae lo que
 *   solo sabe el buscador del servidor: estrellas, repeticiones y orientación votada.
 * - [withDescriptions]: CON conexión, añade a lo que manda el buscador el comentario de cada vía, que solo viene
 *   en los datos de la piedra.
 */
object GuideLines {

    fun fromBlocks(schoolId: String, schoolName: String, blocks: List<Block>): List<LineSearchHit> {
        val sectorNames = blocks.filter { it.type.uppercase() == "ZONE" }.associate { it.id to it.name }
        return blocks
            .filter { it.type.uppercase() !in setOf("ZONE", "PARKING") }
            .flatMap { b ->
                b.facesOrDerived().sortedBy { it.sortOrder }.flatMap { face ->
                    face.lines.sortedBy { it.sortOrder }.map { l ->
                        LineSearchHit(
                            schoolId = schoolId,
                            schoolName = schoolName,
                            blockId = b.id,
                            blockName = b.name,
                            lineId = l.id,
                            lineName = l.displayName,
                            grade = l.grade,
                            sectorName = b.sectorBlockId?.let { sectorNames[it] },
                            photoPath = l.photoPath ?: face.photoPath ?: b.photoPath,
                            linePath = l.linePath,
                            startType = l.startType,
                            lat = b.lat,
                            lon = b.lon,
                            discipline = b.discipline,
                            sectorBlockId = b.sectorBlockId,
                            lineDescription = l.lineDescription?.trim()?.takeIf { it.isNotEmpty() }
                        )
                    }
                }
            }
    }

    fun withDescriptions(hits: List<LineSearchHit>, blocks: List<Block>): List<LineSearchHit> {
        val byLine = blocks.flatMap { it.lines }
            .mapNotNull { l -> l.lineDescription?.trim()?.takeIf { it.isNotEmpty() }?.let { l.id to it } }
            .toMap()
        if (byLine.isEmpty()) return hits
        return hits.map { h -> h.lineId?.let { byLine[it] }?.let { h.copy(lineDescription = it) } ?: h }
    }
}
