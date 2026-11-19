package com.meteomontana.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.meteomontana.android.domain.model.gym.GymKind

/**
 * Los tres colores de un tipo de rocódromo: [fill] (relleno de marcador/icono, con blanco encima), [text]
 * (texto o borde sobre el fondo) y [soft] (fondo suave de las etiquetas).
 */
@Immutable
data class GymTone(val fill: Color, val text: Color, val soft: Color)

private fun isDark(background: Color) = background.luminance() < 0.5f

@Composable
@ReadOnlyComposable
fun gymTone(kind: GymKind): GymTone {
    val dark = isDark(MaterialTheme.colorScheme.background)
    return when (kind) {
        GymKind.COMMERCIAL -> GymTone(
            fill = if (dark) TerraFillDark else TerraFill,
            text = if (dark) TerraDark else Terra,
            soft = (if (dark) TerraDark else Terra).copy(alpha = 0.14f)
        )
        GymKind.MUNICIPAL -> GymTone(
            fill = if (dark) GymMunicipalFillDark else GymMunicipal,
            text = if (dark) GymMunicipalTextDark else GymMunicipal,
            soft = (if (dark) GymMunicipalTextDark else GymMunicipal).copy(alpha = 0.14f)
        )
        GymKind.FREE -> GymTone(
            fill = if (dark) GymFreeFillDark else GymFree,
            text = if (dark) OkDark else Ok,
            soft = (if (dark) OkDark else Ok).copy(alpha = 0.14f)
        )
    }
}

/** El mismo relleno en ARGB para los marcadores del mapa (bitmaps, fuera de Compose). */
fun gymFillArgb(kind: GymKind, dark: Boolean): Int = when (kind) {
    GymKind.COMMERCIAL -> (if (dark) TerraFillDark else TerraFill)
    GymKind.MUNICIPAL -> (if (dark) GymMunicipalFillDark else GymMunicipal)
    GymKind.FREE -> (if (dark) GymFreeFillDark else GymFree)
}.toArgb()
