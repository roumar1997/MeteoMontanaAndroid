package com.meteomontana.android.domain.model

/**
 * Instrucción de dibujo independiente de plataforma.
 * En Android el Composable Canvas las traduce a DrawScope + nativeCanvas.
 * En iOS se traducen a SwiftUI Canvas + CoreGraphics.
 *
 * Coordenadas: píxeles absolutos del canvas (ya multiplicadas por w/h).
 * Colores: ARGB32 como Long (bits 0-31). Usar .toInt() al crear Compose Color.
 */
sealed class DrawOp {

    /** Traza de línea (ya puede incluir el outline oscuro como op previa).
     *  [dashPattern] (on, off) + [dashPhase]: guion personalizado — lo usan las
     *  FRANJAS de tramos compartidos (cada vía pinta su color con fase distinta
     *  y se intercalan). null = sólida, o el guion clásico 20/20 si [dashed]. */
    data class LinePath(
        val pts: List<Pair<Float, Float>>,
        val argb: Long,
        val widthPx: Float,
        val dashed: Boolean,
        val dashPattern: Pair<Float, Float>? = null,
        val dashPhase: Float = 0f
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
