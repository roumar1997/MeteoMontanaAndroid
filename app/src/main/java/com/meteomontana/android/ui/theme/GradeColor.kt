package com.meteomontana.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Color por grado de escalada (sistema francés).
 * Replica la paleta de la PWA en `js/topo.js`.
 */
fun colorForGrade(grade: String?): Color {
    if (grade.isNullOrBlank()) return Color(0xFF9CA3AF)
    val g = grade.lowercase().trim()
    return when {
        g.startsWith("3") || g.startsWith("4") -> Color(0xFF60A5FA) // azul claro
        g.startsWith("5")                       -> Color(0xFF34D399) // verde
        g.startsWith("6a") || g.startsWith("6b")-> Color(0xFFFACC15) // amarillo
        g.startsWith("6c")                      -> Color(0xFFFB923C) // naranja
        g.startsWith("7a") || g.startsWith("7b")-> Color(0xFFEF4444) // rojo
        g.startsWith("7c")                      -> Color(0xFFA855F7) // morado
        g.startsWith("8")                       -> Color(0xFF1F2937) // gris oscuro
        g.startsWith("9")                       -> Color(0xFF000000) // negro
        else                                    -> Color(0xFF9CA3AF)
    }
}
