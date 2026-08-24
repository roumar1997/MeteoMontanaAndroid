package com.meteomontana.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

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

/**
 * El color de un grado ADAPTADO al tema, para pintarlo FUERA de una foto
 * (chips del selector, badges del diario, listas). Sobre la foto de la roca se
 * usa `gradeStyle().stroke` tal cual, que ahí siempre hay contraste.
 *
 * En modo oscuro los dos extremos de la escala se perdían contra el fondo: el
 * negro de ≥8a (#111111) se fundía con #15140F y el blanco de ≤5c+ deslumbraba
 * (Álvaro, 2026-08-24). Aquí se acercan lo justo al centro para que se vean.
 */
/**
 * [gradeStyle] con el color ya adaptado al tema. Es lo que debe usar TODO lo
 * que pinte un grado FUERA de una foto (badges de vía, diario, estadísticas,
 * fichas). Sobre la foto se sigue usando [gradeStyle] a pelo.
 */
@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
fun gradeChipStyle(grade: String?): GradeStyle =
    gradeStyle(grade).copy(stroke = gradeChipColor(grade))

@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
fun gradeChipColor(grade: String?): Color {
    val c = gradeStyle(grade).stroke
    val dark = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() < 0.5f
    if (!dark) return c
    return when {
        c.luminance() < 0.06f -> Color(0xFF4A4A46)   // ≥8a: negro → gris piedra
        c.luminance() > 0.90f -> Color(0xFFD8D3C4)   // ≤5c+: blanco → hueso
        else -> c
    }
}

fun gradeStyle(grade: String?): GradeStyle {
    // Grado DOBLE ("7a/7a+") → colorea como el PRIMERO del rango (GradeRange),
    // en vez de caer en el rosa de "proyecto" por no encajar en el patrón.
    val g = com.meteomontana.android.domain.util.GradeRange.base(grade) ?: ""
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
