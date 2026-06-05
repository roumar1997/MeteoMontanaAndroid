package com.meteomontana.android.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Escala de spacing CUMBRE. Los mismos nombres deben usarse en iOS
 * (Spacing.swift) y en la PWA si algún día se tokenizan. Fuente única
 * de verdad: DESIGN.md en la raíz del repo.
 *
 * Regla: NUNCA pasar dp literales a paddings, gaps o sizes. Si un valor
 * no encaja en la escala, primero discutir si la escala se queda corta.
 */
object Spacing {
    val xs   = 4.dp    // separación mínima, dentro de chips
    val sm   = 8.dp    // gap entre items en una fila densa
    val md   = 12.dp   // padding interno de cards
    val lg   = 16.dp   // padding lateral de pantalla
    val xl   = 24.dp   // separación entre secciones
    val xxl  = 32.dp   // separación entre bloques grandes
    val xxxl = 48.dp   // hero / espacios "respira"
}
