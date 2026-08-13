package com.meteomontana.android.domain.util

import com.meteomontana.android.domain.model.Block

/**
 * Filtro LOCAL por grado dentro de una escuela (ver BLOCK_SEARCH_DESIGN.md §7).
 * Puro Kotlin, sin dependencias de plataforma — Android e iOS comparten el
 * mismo resultado. No llama al servidor: la escuela ya trae todas sus vías.
 */

/**
 * Convierte un grado francés ("6a", "7B+"...) al mismo score numérico que usa
 * [gradeArgb] para colorear ("score = número*100 + letra*10 + (+ ? 1 : 0)").
 * Un único criterio de orden en toda la app. Null si el grado no es
 * reconocible (p.ej. "PROY", vacío) — esas vías no entran en ningún filtro.
 */
fun gradeScore(grade: String?): Int? {
    val g = (grade ?: "").trim().uppercase()
    val re = Regex("^([3-9])([ABCD])?(\\+)?$")
    val m = re.matchEntire(g) ?: return null
    val num = m.groupValues[1].toInt()
    val letterScore = mapOf("A" to 0, "B" to 1, "C" to 2, "D" to 3)[
        m.groupValues[2].ifEmpty { "A" }
    ] ?: 0
    val plus = if (m.groupValues[3] == "+") 1 else 0
    return num * 100 + letterScore * 10 + plus
}

/** Resultado del filtro: qué piedras y qué vías concretas caen en rango. */
data class GradeFilterResult(
    val matchingBlockIds: Set<String>,
    val matchingLineIds: Set<String>,
    val totalLines: Int,
    val matchingLines: Int
) {
    /** Sin filtro activo (min y max ambos null): nada se atenúa. */
    val isActive: Boolean get() = true
}

/**
 * @param minGrade grado mínimo (ej. "7A"), null = sin suelo
 * @param maxGrade grado máximo (ej. "7B+"), null = sin techo
 * Devuelve qué piedras tienen AL MENOS una vía en rango, y qué vías concretas
 * caen en rango (para atenuar el resto dentro de la ficha, no ocultarlas).
 */
fun filterBlocksByGrade(blocks: List<Block>, minGrade: String?, maxGrade: String?): GradeFilterResult {
    val min = gradeScore(minGrade) ?: Int.MIN_VALUE
    val max = gradeScore(maxGrade) ?: Int.MAX_VALUE

    val matchingBlocks = mutableSetOf<String>()
    val matchingLines = mutableSetOf<String>()
    var total = 0

    for (block in blocks) {
        for (line in block.lines) {
            total++
            val score = gradeScore(line.grade) ?: continue
            if (score in min..max) {
                matchingLines += line.id
                matchingBlocks += block.id
            }
        }
    }
    return GradeFilterResult(matchingBlocks, matchingLines, total, matchingLines.size)
}
