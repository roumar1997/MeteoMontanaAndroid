package com.meteomontana.android.ui.screens.detail.guide

import com.meteomontana.android.domain.model.LineSearchHit
import com.meteomontana.android.domain.usecase.schools.GuideBlock
import com.meteomontana.android.domain.usecase.schools.GuideSector
import com.meteomontana.android.domain.util.gradeScore
import com.meteomontana.android.ui.screens.topo.parseLineStroke
import com.meteomontana.android.domain.util.TopoLineData

/** Una cara de la piedra: una foto y las vías dibujadas sobre ELLA (numeradas 1, 2, 3… como en la ficha). */
data class GuideFace(val blockId: String, val photo: String?, val lines: List<LineSearchHit>) {
    val id: String get() = "$blockId|${photo ?: ""}"

    /** Las líneas listas para `renderTopo`, en el orden de la cara (el número es su posición + 1). */
    fun topoLines(strokeWidth: Float): List<TopoLineData> = lines.map { h ->
        TopoLineData(
            name = null, grade = h.grade, startType = h.startType,
            points = parseLineStroke(h.linePath).points.map { it.x to it.y },
            strokeWidthPx = strokeWidth
        )
    }
}

/** Las vías de la piedra agrupadas por la foto en la que están dibujadas, en el orden en que aparecen. */
val GuideBlock.faces: List<GuideFace>
    get() {
        val byPhoto = LinkedHashMap<String, MutableList<LineSearchHit>>()
        for (h in lines) byPhoto.getOrPut(h.photoPath ?: "") { mutableListOf() }.add(h)
        return byPhoto.map { (k, v) -> GuideFace(blockId, k.ifEmpty { null }, v) }
    }

/** Una vía del listado del inicio de sector, con su piedra (para saltar a ella). */
data class GuideChip(val block: GuideBlock, val hit: LineSearchHit)

/** TODAS las vías del sector agrupadas por grado, de fácil a difícil ("?" y proyectos al final). */
fun GuideSector.linesByGrade(): List<Pair<String, List<GuideChip>>> {
    val groups = LinkedHashMap<String, MutableList<GuideChip>>()
    for (b in blocks) for (h in b.lines) groups.getOrPut(h.grade ?: "?") { mutableListOf() }.add(GuideChip(b, h))
    return groups.entries.sortedBy { gradeScore(it.key) ?: 10_000 }.map { it.key to it.value }
}
