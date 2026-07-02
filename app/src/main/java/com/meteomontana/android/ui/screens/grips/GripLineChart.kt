package com.meteomontana.android.ui.screens.grips

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Un punto de la gráfica: fuerza (kg) + de qué mano es (para pintarlo del
 *  color de esa mano cuando aplica — ver GRIPS_DESIGN.md sección 4.4). */
data class ChartPoint(val kg: Float, val hand: String? = null)

/**
 * Gráfica de líneas simple dibujada a mano en Canvas (sin librería externa —
 * evita arriesgar una dependencia sin poder probarla en runtime). Pensada
 * para tiempo real: si [targetMin]/[targetMax] se pasan, dibuja la banda de
 * rango objetivo detrás de la línea.
 */
@Composable
fun GripLineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    targetMin: Float? = null,
    targetMax: Float? = null,
    leftColor: Color = Color(0xFFB5654A),   // terra
    rightColor: Color = Color(0xFF3A6B8A),  // azul
    defaultColor: Color? = null
) {
    val bg = MaterialTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.outline
    val targetBandColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val fallbackLine = defaultColor ?: MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.fillMaxWidth().height(180.dp).background(bg)) {
        val maxKg = (points.maxOfOrNull { it.kg } ?: 1f).coerceAtLeast(targetMax ?: 0f) * 1.15f
        val safeMax = if (maxKg <= 0f) 1f else maxKg
        val w = size.width
        val h = size.height

        fun yFor(kg: Float) = h - (kg / safeMax) * h

        // Banda del rango objetivo
        if (targetMin != null && targetMax != null) {
            drawRect(
                color = targetBandColor,
                topLeft = Offset(0f, yFor(targetMax)),
                size = androidx.compose.ui.geometry.Size(w, yFor(targetMin) - yFor(targetMax))
            )
        }

        // Líneas de rejilla horizontales (25/50/75/100%)
        listOf(0.25f, 0.5f, 0.75f, 1f).forEach { frac ->
            val y = h - h * frac
            drawLine(gridColor.copy(alpha = 0.3f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }

        if (points.size < 2) return@Canvas
        val stepX = w / (points.size - 1).coerceAtLeast(1)
        for (i in 0 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val color = when (p1.hand) {
                "LEFT" -> leftColor
                "RIGHT" -> rightColor
                else -> fallbackLine
            }
            drawLine(
                color = color,
                start = Offset(i * stepX, yFor(p0.kg)),
                end = Offset((i + 1) * stepX, yFor(p1.kg)),
                strokeWidth = 4f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}
