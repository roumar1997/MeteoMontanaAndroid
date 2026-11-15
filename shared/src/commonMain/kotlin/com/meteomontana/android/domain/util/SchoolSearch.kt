package com.meteomontana.android.domain.util

import com.meteomontana.android.domain.model.Block

/**
 * Buscador de la lupa de una escuela: vías, bloques, sectores y parkings. Lógica pura, compartida con iOS.
 * Sectores y parkings van primero (son pocos y llevan al mapa); después vías y piedras en el orden de la escuela.
 */
enum class SchoolSearchKind { ROUTE, BOULDER, SECTOR, PARKING }

data class SchoolSearchHit(
    val kind: SchoolSearchKind,
    /** Texto principal: el nombre. */
    val label: String,
    /** Id de la vía, solo si el resultado es una vía concreta. */
    val lineId: String?,
    /** Nombre limpio (sin grado) para abrir por nombre. */
    val name: String,
    /** Id del bloque/sector/parking al que lleva el resultado. */
    val blockId: String,
    /** Nombre de la piedra que contiene la vía; null si el resultado ES una piedra, sector o parking. */
    val parentName: String?,
    /** Vías de la piedra a la que pertenece el resultado (vía o piedra); 0 en sectores y parkings. */
    val lineCount: Int,
    /** Grado de la vía (para el chip de color); null si no es una vía o no lo tiene. */
    val grade: String? = null
)

const val SCHOOL_SEARCH_MIN_CHARS = 2

fun searchSchool(blocks: List<Block>, query: String, limit: Int = 10): List<SchoolSearchHit> {
    val q = query.trim()
    if (q.length < SCHOOL_SEARCH_MIN_CHARS) return emptyList()
    fun Block.matches() = name.contains(q, ignoreCase = true)
    fun Block.stoneKind() =
        if (discipline.equals("ROUTE", ignoreCase = true)) SchoolSearchKind.ROUTE else SchoolSearchKind.BOULDER

    val hits = mutableListOf<SchoolSearchHit>()
    blocks.filter { it.type == "ZONE" && it.matches() }.forEach {
        hits += SchoolSearchHit(SchoolSearchKind.SECTOR, it.name, null, it.name, it.id, null, 0)
    }
    blocks.filter { it.type == "PARKING" && it.matches() }.forEach {
        hits += SchoolSearchHit(SchoolSearchKind.PARKING, it.name, null, it.name, it.id, null, 0)
    }
    blocks.filter { it.type == "BLOCK" }.forEach { b ->
        b.lines.forEach { l ->
            if (l.name.contains(q, ignoreCase = true)) hits += SchoolSearchHit(
                kind = b.stoneKind(),
                label = l.name, grade = l.grade?.takeIf { it.isNotBlank() },
                lineId = l.id, name = l.name, blockId = b.id, parentName = b.name, lineCount = b.lines.size
            )
        }
        if (b.matches()) hits += SchoolSearchHit(
            b.stoneKind(), b.name, null, b.name, b.id, null, b.lines.size
        )
    }
    return hits.take(limit)
}
