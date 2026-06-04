package com.meteomontana.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Color por grado de escalada (sistema francés).
 * Replica EXACTAMENTE la paleta de la PWA (`js/utils/topo-draw.js`).
 *
 * ≤5c+   → blanco (texto interior oscuro)
 * 6a-6b+ → verde
 * 6c-6c+ → azul
 * 7a-7a+ → morado
 * 7b-7c+ → rojo
 * ≥8a    → negro
 * proyecto/sin grado → rosa punteado
 */
data class GradeStyle(val stroke: Color, val dashed: Boolean, val dark: Boolean)

fun gradeStyle(grade: String?): GradeStyle {
    val g = (grade ?: "").trim().uppercase()
    if (g.isEmpty() || g == "PROY" || g == "PROYECTO" || g == "?") {
        return GradeStyle(Color(0xFFFF4FA3), dashed = true, dark = false)
    }
    val re = Regex("^([3-9])([ABCD])?(\\+)?$")
    val m = re.matchEntire(g) ?: return GradeStyle(Color(0xFFFF4FA3), dashed = true, dark = false)
    val num = m.groupValues[1].toInt()
    val letterScore = mapOf("A" to 0, "B" to 1, "C" to 2, "D" to 3)[m.groupValues[2].ifEmpty { "A" }] ?: 0
    val plus = if (m.groupValues[3] == "+") 1 else 0
    val score = num * 100 + letterScore * 10 + plus
    return when {
        score <= 521 -> GradeStyle(Color(0xFFFFFFFF), false, dark = true)
        score <= 611 -> GradeStyle(Color(0xFF1FA84E), false, false)
        score <= 621 -> GradeStyle(Color(0xFF1D6DD6), false, false)
        score <= 701 -> GradeStyle(Color(0xFF8E3FBF), false, false)
        score <= 721 -> GradeStyle(Color(0xFFD62828), false, false)
        else         -> GradeStyle(Color(0xFF111111), false, false)
    }
}

/** Helper compatible con el uso anterior — solo color. */
fun colorForGrade(grade: String?): Color = gradeStyle(grade).stroke
