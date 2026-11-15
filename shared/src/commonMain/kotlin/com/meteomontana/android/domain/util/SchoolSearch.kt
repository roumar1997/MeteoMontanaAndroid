package com.meteomontana.android.domain.util

import com.meteomontana.android.domain.model.Block

/**
 * Buscador de la lupa de una escuela: vías, bloques, sectores y parkings. Lógica pura, compartida con iOS.
 * Sectores y parkings van primero (son pocos y llevan al mapa); después vías y piedras en el orden de la escuela.
 */
enum class SchoolSearchKind { ROUTE, BOULDER, SECTOR, PARKING }

data class SchoolSearchHit(
    val kind: SchoolSearchKind,
    /** Texto principal: nombre (y grado si es una vía). */
    val label: String,
    /** Id de la vía, solo si el resultado es una vía concreta. */
    val lineId: String?,
    /** Nombre limpio (sin grado) para abrir por nombre. */
    val name: String,
    /** Id del bloque/sector/parking al que lleva el resultado. */
    val blockId: String,
    /** Nombre de la piedra que contiene la vía; null si el resultado ES una piedra, sector o parking. */
    val parentName: String?,
    /** Vías de la piedra cuando el resultado es una piedra (para «N vías»); 0 en el resto. */
    val lineCount: Int
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
                label = l.name + (l.grade?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                lineId = l.id, name = l.name, blockId = b.id, parentName = b.name, lineCount = 0
            )
        }
        if (b.matches()) hits += SchoolSearchHit(
            b.stoneKind(), b.name, null, b.name, b.id, null, b.lines.size
        )
    }
    return hits.take(limit)
}
