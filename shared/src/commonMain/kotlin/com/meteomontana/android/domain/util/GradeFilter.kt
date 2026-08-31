package com.meteomontana.android.domain.util

import com.meteomontana.android.domain.model.Block

/**
 * Filtro LOCAL por grado dentro de una escuela (ver BLOCK_SEARCH_DESIGN.md §7).
 * Puro Kotlin, sin dependencias de plataforma — Android e iOS comparten el
 * mismo resultado. No llama al servidor: la escuela ya trae todas sus vías.
 * Selección MÚLTIPLE de grados exactos (chips, como en el diario) — no rango.
 */

/**
 * Convierte un grado francés ("6a", "7B+"...) al mismo score numérico que usa
 * [gradeArgb] para colorear ("score = número*100 + letra*10 + (+ ? 1 : 0)").
 * Un único criterio de orden en toda la app. Null si el grado no es
 * reconocible (p.ej. "PROY", vacío) — esas vías no entran en ningún filtro.
 */
fun gradeScore(grade: String?): Int? {
    // Un grado DOBLE ("7a/7a+") puntúa como el primero del rango: así ordena,
    // colorea y filtra como el 7a, en vez de caer fuera de todo (GradeRange).
    val g = GradeRange.base(grade) ?: ""
    val re = Regex("^([3-9])([ABCD])?(\\+)?$")
    val m = re.matchEntire(g) ?: return null
    val num = m.groupValues[1].toInt()
    val letterScore = mapOf("A" to 0, "B" to 1, "C" to 2, "D" to 3)[
        m.groupValues[2].ifEmpty { "A" }
    ] ?: 0
    val plus = if (m.groupValues[3] == "+") 1 else 0
    return num * 100 + letterScore * 10 + plus
}

/** Grados que EXISTEN de verdad en estas piedras, ordenados de más difícil a más fácil. */
fun availableGrades(blocks: List<Block>): List<String> =
    blocks.flatMap { it.lines }
        .mapNotNull { it.grade?.trim()?.uppercase()?.takeIf { g -> gradeScore(g) != null } }
        .distinct()
        .sortedByDescending { gradeScore(it) }

/** Una vía que ha caído dentro de la selección de grados, con su piedra de origen. */
data class GradeMatch(
    val lineId: String,
    val lineName: String,
    val blockId: String,
    val blockName: String,
    val grade: String
)

/** Resultado del filtro: qué piedras/vías caen en la selección, agrupadas por grado. */
data class GradeFilterResult(
    val matchingBlockIds: Set<String>,
    val matchingLineIds: Set<String>,
    val totalLines: Int,
    /** Grado (desc.) → vías con ese grado, en el mismo orden que [availableGrades]. */
    val groups: List<Pair<String, List<GradeMatch>>>
) {
    val matchingLines: Int get() = matchingLineIds.size
}

/**
 * @param selectedGrades grados exactos elegidos (ej. {"6A+", "7B"}), vacío = sin filtro.
 * Devuelve qué piedras tienen AL MENOS una vía seleccionada, las vías concretas,
 * y esas mismas vías agrupadas por grado para listarlas en la UI.
 */
fun filterBlocksByGrades(blocks: List<Block>, selectedGrades: Set<String>): GradeFilterResult {
    val selected = selectedGrades.map { it.trim().uppercase() }.toSet()
    val matchingBlocks = mutableSetOf<String>()
    val matchingLines = mutableSetOf<String>()
    val byGrade = linkedMapOf<String, MutableList<GradeMatch>>()
    var total = 0

    for (block in blocks) {
        for (line in block.lines) {
            total++
            val g = line.grade?.trim()?.uppercase() ?: continue
            if (g in selected) {
                matchingLines += line.id
                matchingBlocks += block.id
                byGrade.getOrPut(g) { mutableListOf() } +=
                    GradeMatch(line.id, line.displayName, block.id, block.name, g)
            }
        }
    }
    val groups = byGrade.entries
        .sortedByDescending { gradeScore(it.key) }
        .map { it.key to it.value.toList() }

    return GradeFilterResult(matchingBlocks, matchingLines, total, groups)
}
