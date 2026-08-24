package com.meteomontana.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

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

/**
 * Fondo de los botones "negros de marca" (entrar con Google, guardar, chip
 * seleccionado…). NO es [Ink]: Ink es color de TEXTO y en oscuro se invierte a
 * casi blanco. Estos botones tienen que seguir siendo oscuros en los dos temas,
 * con texto blanco encima.
 *
 * Se hardcodeaba `Color(0xFF1C1C1A)` en ~19 sitios; en modo oscuro el fondo de
 * pantalla es #15140F y el botón desaparecía (Álvaro, 2026-08-24: revisión de
 * modo oscuro). Usa siempre este par, nunca el literal.
 */
val InkButton     = Color(0xFF1C1C1A)
val InkButtonDark = Color(0xFF2A281F)

/**
 * El color de fondo correcto para esos botones según el tema ACTIVO.
 *
 * Mira la luminancia del esquema en vez de `isSystemInDarkTheme()` a propósito:
 * el tema lo elige el usuario dentro de la app (MainActivity pasa `isDark`), así
 * que preguntarle al sistema daría el valor equivocado con el tema forzado.
 */
@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
fun inkButtonColor(): Color =
    if (androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() < 0.5f)
        InkButtonDark else InkButton

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
    score >= 90 -> Color(0xFF3F6B4A) // --ok fuerte
    score >= 80 -> Color(0xFF5B7E3F)
    score >= 70 -> Color(0xFF7D9A4E)
    score >= 60 -> Color(0xFFB48A2E)
    score >= 50 -> Color(0xFFB45309) // --warn
    score >= 40 -> Color(0xFFA0420B)
    score >= 30 -> Color(0xFF9A3412) // --bad
    score >= 20 -> Color(0xFF7C2410)
    else        -> Color(0xFF5A1E08)
}

// El número del score se pinta SIEMPRE en blanco (igual que iOS). Antes los
// scores 40-79 usaban texto casi negro, que sobre el naranja (40-59) no se leía.
fun scoreTextColor(score: Int): Color = Color.White
