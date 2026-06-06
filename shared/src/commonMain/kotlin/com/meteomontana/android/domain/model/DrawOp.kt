package com.meteomontana.android.domain.model

/**
 * Instrucción de dibujo independiente de plataforma.
 * En Android el Composable Canvas las traduce a DrawScope + nativeCanvas.
 * En iOS (Fase 3) se traducirán a SwiftUI Canvas + CoreGraphics.
 *
 * Coordenadas: píxeles absolutos del canvas (ya multiplicadas por w/h).
 * Colores: ARGB32 como Long (bits 0-31). Usar .toInt() al crear Compose Color.
 */
sealed class DrawOp {

    /** Traza de línea (ya puede incluir el outline oscuro como op previa). */
    data class LinePath(
        val pts: List<Pair<Float, Float>>,
        val argb: Long,
        val widthPx: Float,
        val dashed: Boolean
    ) : DrawOp()

    /** Círculo relleno. */
    data class FilledCircle(
        val cx: Float, val cy: Float,
        val radius: Float,
        val argb: Long
    ) : DrawOp()

    /** Borde de círculo (sin relleno). */
    data class CircleStroke(
        val cx: Float, val cy: Float,
        val radius: Float,
        val argb: Long,
        val strokePx: Float
    ) : DrawOp()

    /** Etiqueta de texto centrada en (cx, cy + baselineOffsetPx). */
    data class TextLabel(
        val cx: Float, val cy: Float,
        val text: String,
        val argb: Long,
        val sizePx: Float,
        val bold: Boolean,
        val baselineOffsetPx: Float
    ) : DrawOp()
}
