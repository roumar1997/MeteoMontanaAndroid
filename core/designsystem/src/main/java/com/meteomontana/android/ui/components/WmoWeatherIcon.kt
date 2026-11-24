package com.meteomontana.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Icono meteorológico SVG line-art, equivalente exacto a `wmoSvg()` de la PWA
 * (`js/utils/weather-icons.js`). Usa códigos WMO de Open-Meteo.
 *
 * El viewport de los paths es 24×24 (igual que la PWA).
 * DrawScope.scale() convierte del viewport al tamaño real sin tocar los paths.
 * El stroke-width 1.4 se escala proporcionalmente — igual que en SVG.
 */
@Composable
fun WmoWeatherIcon(
    code: Int,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val pathStrings = remember(code) { wmoSvgPaths(code) }

    Canvas(modifier = modifier.size(size)) {
        val sx = this.size.width  / 24f
        val sy = this.size.height / 24f

        // stroke-width="1.4" del SVG original, escalado con el viewport
        val stroke = Stroke(
            width = 1.4f * sx,
            cap   = StrokeCap.Round,
            join  = StrokeJoin.Round
        )

        scale(sx, sy, pivot = Offset.Zero) {
            pathStrings.forEach { pathStr ->
                val path = PathParser().parsePathString(pathStr).toPath()
                drawPath(path, brush = SolidColor(tint), style = stroke)
            }
        }
    }
}

/**
 * Path strings SVG para cada categoría WMO. Traducción directa de `wmoSvg()`
 * en weather-icons.js. Viewport 24×24, stroke="currentColor" stroke-width="1.4".
 *
 * WMO codes relevantes de Open-Meteo:
 *   0       = despejado
 *   1-3     = parcialmente nublado
 *   45,48   = niebla
 *   51-67   = llovizna / lluvia
 *   71-77   = nieve
 *   80-82   = chubascos
 *   95-99   = tormenta
 */
private fun wmoSvgPaths(code: Int): List<String> = when {

    // ── Despejado (0) ────────────────────────────────────────────────────────
    code == 0 -> listOf(
        // Círculo central del sol
        "M12 8 A4 4 0 1 0 12 16 A4 4 0 1 0 12 8 Z",
        // Rayos (líneas cortas en 8 direcciones)
        "M12 2 L12 5 M12 19 L12 22 " +
        "M2 12 L5 12 M19 12 L22 12 " +
        "M5.05 5.05 L7.17 7.17 M16.83 16.83 L18.95 18.95 " +
        "M5.05 18.95 L7.17 16.83 M16.83 7.17 L18.95 5.05"
    )

    // ── Poco / parcialmente nublado (1-3) ────────────────────────────────────
    code <= 3 -> listOf(
        // Sol pequeño arriba-izquierda
        "M9 6 A3 3 0 1 0 9 12 A3 3 0 1 0 9 6 Z",
        // Nube en primer plano
        "M15 18 A4 4 0 0 0 15 10 A5 5 0 0 0 5.5 12 A4 4 0 0 0 5.5 18 Z"
    )

    // ── Niebla (45, 48) ──────────────────────────────────────────────────────
    code == 45 || code == 48 -> listOf(
        "M3 8 L21 8 M3 12 L17 12 M3 16 L21 16 M3 20 L15 20"
    )

    // ── Llovizna / lluvia (51-67) ────────────────────────────────────────────
    code <= 67 -> listOf(
        "M15 18 A4 4 0 0 0 15 10 A5 5 0 0 0 5.5 12 A4 4 0 0 0 5.5 18 Z",
        "M8 20 L7 22 M12 20 L11 22 M16 20 L15 22"
    )

    // ── Nieve (71-77) ────────────────────────────────────────────────────────
    code <= 77 -> listOf(
        "M15 16 A4 4 0 0 0 15 8 A5 5 0 0 0 5.5 10 A4 4 0 0 0 5.5 16 Z",
        // Puntos de nieve (líneas cortas)
        "M8 19 L8.5 20 M12 19 L12.5 20 M16 19 L16.5 20"
    )

    // ── Chubascos intensos (80-82) ───────────────────────────────────────────
    code <= 82 -> listOf(
        "M15 16 A4 4 0 0 0 15 8 A5 5 0 0 0 5.5 10 A4 4 0 0 0 5.5 16 Z",
        "M7 19 L8 21 M11 19 L12 21 M15 19 L16 21"
    )

    // ── Tormenta eléctrica (95-99) / resto ───────────────────────────────────
    else -> listOf(
        "M15 14 A4 4 0 0 0 15 6 A5 5 0 0 0 5.5 8 A4 4 0 0 0 5.5 14 Z",
        // Rayo
        "M11 14 L9 18 L12 18 L10 22"
    )
}
