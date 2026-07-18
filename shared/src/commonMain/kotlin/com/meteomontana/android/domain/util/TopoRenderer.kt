package com.meteomontana.android.domain.util

import com.meteomontana.android.domain.model.DrawOp

/**
 * Datos de una línea/vía del topo. Puro Kotlin, sin tipos Compose ni Android.
 * Listo para mover a commonMain en Fase 2.
 *
 * @param points Coordenadas normalizadas (0..1) como (x, y)
 * @param strokeWidthPx Grosor de línea en px. Viewer usa 5f; editor usa 8f (seleccionada) / 5f.
 */
data class TopoLineData(
    val name: String?,
    val grade: String?,
    val startType: String?,
    val points: List<Pair<Float, Float>>,
    val strokeWidthPx: Float = 5f
)

/**
 * Calcula el color ARGB32 (como Long) para un grado de escalada.
 * Replica la paleta de la PWA (js/utils/topo-draw.js).
 * Triple: (argb, dashed, dark). dark=true → el color es blanco y el texto debe ser negro.
 */
fun gradeArgb(grade: String?): Triple<Long, Boolean, Boolean> {
    val g = (grade ?: "").trim().uppercase()
    if (g.isEmpty() || g == "PROY" || g == "PROYECTO" || g == "?") {
        return Triple(0xFFFF4FA3L, true, false)
    }
    val re = Regex("^([3-9])([ABCD])?(\\+)?$")
    val m = re.matchEntire(g) ?: return Triple(0xFFFF4FA3L, true, false)
    val num = m.groupValues[1].toInt()
    val letterScore = mapOf("A" to 0, "B" to 1, "C" to 2, "D" to 3)[
        m.groupValues[2].ifEmpty { "A" }
    ] ?: 0
    val plus = if (m.groupValues[3] == "+") 1 else 0
    val score = num * 100 + letterScore * 10 + plus
    return when {
        score <= 521 -> Triple(0xFFFFFFFFL, false, true)
        score <= 611 -> Triple(0xFF1FA84EL, false, false)
        score <= 621 -> Triple(0xFF1D6DD6L, false, false)
        score <= 701 -> Triple(0xFF8E3FBFL, false, false)
        score <= 721 -> Triple(0xFFD62828L, false, false)
        else         -> Triple(0xFF111111L, false, false)
    }
}

/**
 * Convierte una lista de líneas topo en instrucciones de dibujo independientes de plataforma.
 *
 * Puro Kotlin — sin imports de Android ni Compose. Listo para commonMain en Fase 2.
 *
 * @param lines  Líneas a dibujar (coords normalizadas 0..1)
 * @param w      Ancho del canvas en píxeles
 * @param h      Alto del canvas en píxeles
 * @param badgeR Radios del badge numérico (exterior, interior)
 * @param badgeTextPx Tamaño de texto del badge numérico y offset de baseline
 * @param startR Radios del badge de tipo de inicio (exterior, interior)
 * @param startTextPx Tamaño de texto del badge de inicio y offset de baseline
 */
fun renderTopo(
    lines: List<TopoLineData>,
    w: Float,
    h: Float,
    badgeR: Pair<Float, Float> = 14f to 11f,
    badgeTextPx: Pair<Float, Float> = 22f to 7f,
    startR: Pair<Float, Float> = 22f to 18f,
    startTextPx: Pair<Float, Float> = 18f to 6f
): List<DrawOp> {
    val ops = mutableListOf<DrawOp>()

    lines.forEachIndexed { idx, line ->
        if (line.points.isEmpty()) return@forEachIndexed
        val (strokeArgb, dashed, dark) = gradeArgb(line.grade)
        val pts = line.points.map { (nx, ny) -> nx * w to ny * h }
        val textArgb = if (dark) 0xFF000000L else 0xFFFFFFFFL

        // Outline para que líneas blancas sean visibles sobre cualquier foto
        if (dark) {
            ops += DrawOp.LinePath(pts, 0xCC000000L, line.strokeWidthPx + 4f, dashed)
        }
        ops += DrawOp.LinePath(pts, strokeArgb, line.strokeWidthPx, dashed)

        // Badge numérico en el punto de inicio
        val (fx, fy) = pts.first()
        ops += DrawOp.FilledCircle(fx, fy, badgeR.first, 0xFFFFFFFFL)
        ops += DrawOp.FilledCircle(fx, fy, badgeR.second, strokeArgb)
        ops += DrawOp.TextLabel(fx, fy, "${idx + 1}", textArgb, badgeTextPx.first, bold = true, badgeTextPx.second)

        // Badge de tipo de inicio en el punto final
        val label = when (line.startType?.uppercase()) {
            "PIE", "STAND"  -> "PIE"
            "SIT"           -> "SIT"
            "SEMI"          -> "SEM"
            "LANCE", "JUMP" -> "LAN"
            "TRAV"          -> "TRV"
            else            -> null
        }
        if (label != null) {
            val (lx, ly) = pts.last()
            val haloArgb = if (dark) 0xFF000000L else 0xFFFFFFFFL
            ops += DrawOp.FilledCircle(lx, ly, startR.first, haloArgb)
            ops += DrawOp.FilledCircle(lx, ly, startR.second, strokeArgb)
            if (dark) ops += DrawOp.CircleStroke(lx, ly, startR.second, 0xFF000000L, 2f)
            ops += DrawOp.TextLabel(lx, ly, label, textArgb, startTextPx.first, bold = true, startTextPx.second)
        }
    }
    return ops
}
