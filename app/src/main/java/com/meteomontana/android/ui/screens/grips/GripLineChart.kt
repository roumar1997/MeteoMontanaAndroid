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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Un punto de la gráfica: fuerza (kg) + de qué mano es (para pintarlo del
 *  color de esa mano cuando aplica — ver GRIPS_DESIGN.md sección 4.4). */
data class ChartPoint(val kg: Float, val hand: String? = null)

/**
 * Gráfica de fuerza en tiempo real, estilo ECG:
 * - VENTANA DESLIZANTE: se pintan solo los últimos [windowSize] puntos, cada
 *   punto ocupa un ancho FIJO y la curva se desplaza hacia la izquierda —
 *   nada de comprimir toda la sesión en el ancho (eso hacía que la línea se
 *   aplastara y "bailara").
 * - ESCALA Y ESTABLE: [yMaxKg] la fija el llamador (p.ej. pico de la sesión
 *   con margen) para que el eje no salte con cada lectura.
 */
@Composable
fun GripLineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    targetMin: Float? = null,
    targetMax: Float? = null,
    yMaxKg: Float? = null,
    windowSize: Int = 160, // ~20s a 8Hz
    leftColor: Color = Color(0xFFB5654A),   // terra
    rightColor: Color = Color(0xFF3A6B8A),  // azul
    defaultColor: Color? = null
) {
    val bg = MaterialTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.outline
    val targetBandColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val fallbackLine = defaultColor ?: MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.fillMaxWidth().height(180.dp).background(bg)) {
        val visible = points.takeLast(windowSize)
        val dataMax = visible.maxOfOrNull { it.kg } ?: 1f
        val safeMax = maxOf(yMaxKg ?: 0f, dataMax, targetMax ?: 0f, 1f) * 1.10f
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

        // Rejilla horizontal (25/50/75/100%)
        listOf(0.25f, 0.5f, 0.75f, 1f).forEach { frac ->
            val y = h - h * frac
            drawLine(gridColor.copy(alpha = 0.3f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }

        if (visible.size < 2) return@Canvas
        // Ancho FIJO por punto: la curva entra por la derecha y se desplaza.
        val stepX = w / (windowSize - 1).toFloat()
        val startX = w - (visible.size - 1) * stepX

        // Un Path por tramo de color (mano) — mucho más fluido que N drawLine.
        var segStart = 0
        var i = 1
        while (i <= visible.size) {
            val segColor = when (visible[(i - 1).coerceAtLeast(segStart)].hand) {
                "LEFT" -> leftColor
                "RIGHT" -> rightColor
                else -> fallbackLine
            }
            val colorChanged = i < visible.size && when (visible[i].hand) {
                "LEFT" -> leftColor
                "RIGHT" -> rightColor
                else -> fallbackLine
            } != segColor
            if (i == visible.size || colorChanged) {
                val path = Path()
                path.moveTo(startX + segStart * stepX, yFor(visible[segStart].kg))
                for (j in segStart + 1 until i) {
                    path.lineTo(startX + j * stepX, yFor(visible[j].kg))
                }
                if (i < visible.size) path.lineTo(startX + i * stepX, yFor(visible[i].kg))
                drawPath(path, segColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
                segStart = i
            }
            i++
        }

        // Punto "en vivo" al final de la curva.
        val last = visible.last()
        val lastColor = when (last.hand) {
            "LEFT" -> leftColor
            "RIGHT" -> rightColor
            else -> fallbackLine
        }
        drawCircle(lastColor, radius = 7f,
            center = Offset(startX + (visible.size - 1) * stepX, yFor(last.kg)))
    }
}
