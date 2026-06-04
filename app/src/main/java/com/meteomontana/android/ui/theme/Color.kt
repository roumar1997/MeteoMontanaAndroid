package com.meteomontana.android.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
// CUMBRE — light mode (papel, tinta, terracota)
// Tokens copiados de css/tokens.css de la PWA.
// =============================================================================
val Bg      = Color(0xFFF5F3EE)
val Paper   = Color(0xFFEBE7DD)
val Paper2  = Color(0xFFF0EAD8)

val Ink     = Color(0xFF1C1C1A)
val Ink2    = Color(0xFF5A574F)
val Ink3    = Color(0xFF8A8478)
val Rule    = Color(0xFFD6D2C4)

val Terra   = Color(0xFFC2410C)
val TerraBg = Color(0xFFFDE4D3)
val Moss    = Color(0xFF5E6B4F)

val Ok      = Color(0xFF3F6B4A)
val Warn    = Color(0xFFB45309)
val Bad     = Color(0xFF9A3412)

val Rain    = Color(0xFF2563C7)
val Wind    = Color(0xFF4A7C3F)

// =============================================================================
// CUMBRE — dark mode
// =============================================================================
val BgDark      = Color(0xFF15140F)
val PaperDark   = Color(0xFF1D1C17)
val Paper2Dark  = Color(0xFF211F19)

val InkDark     = Color(0xFFECE7D8)
val Ink2Dark    = Color(0xFFA8A397)
val Ink3Dark    = Color(0xFF6E6A5F)
val RuleDark    = Color(0xFF2A281F)

val TerraDark   = Color(0xFFE0612B)
val MossDark    = Color(0xFF7D8A6A)
val OkDark      = Color(0xFF7DA068)
val WarnDark    = Color(0xFFD6904A)
val BadDark     = Color(0xFFC9543B)

// =============================================================================
// Score heatmap — colores exactos de tokens.css
// =============================================================================
fun scoreColor(score: Int): Color = when {
    score >= 90 -> Color(0xFF5E8B50)
    score >= 80 -> Color(0xFF82A76E)
    score >= 70 -> Color(0xFFB7C089)
    score >= 60 -> Color(0xFFE3D599)
    score >= 50 -> Color(0xFFE8B878)
    score >= 40 -> Color(0xFFD99A5A)
    score >= 30 -> Color(0xFFC2410C)
    score >= 20 -> Color(0xFF9A3412)
    else        -> Color(0xFF5A1E08)
}

fun scoreTextColor(score: Int): Color =
    if (score in 40..79) Color(0xFF1C1C1A) else Color.White
