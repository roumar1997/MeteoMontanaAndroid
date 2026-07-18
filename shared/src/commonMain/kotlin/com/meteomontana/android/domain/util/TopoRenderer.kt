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

// ─── Tramos compartidos entre vías ───────────────────────────────────────────
// Cuando dos vías comparten camino (el editor "imanta" el trazo a la vía
// existente y COPIA sus puntos), el tramo común se pinta de un color especial
// para que no se pisen los colores de grado. La detección es por IGUALDAD de
// segmentos (mismos dos vértices, en cualquier orden): como el imán copia los
// puntos exactos, no hay que "adivinar" cercanías al pintar.

/** Color del tramo compartido por varias vías (naranja: no choca con la paleta
 *  de grados ni con el rosa discontinuo de proyecto). */
const val SHARED_SEGMENT_ARGB = 0xFFFF9500L

private fun pointKey(p: Pair<Float, Float>): String {
    // Redondeo a 4 decimales en coords normalizadas 0..1: robusto frente al
    // viaje JSON de los floats; el imán copia los valores exactos.
    fun r(v: Float) = kotlin.math.round(v * 10000f).toInt()
    return "${r(p.first)},${r(p.second)}"
}

private fun segmentKey(a: Pair<Float, Float>, b: Pair<Float, Float>): String {
    val ka = pointKey(a); val kb = pointKey(b)
    return if (ka <= kb) "$ka|$kb" else "$kb|$ka"
}

/** Claves de los segmentos presentes en DOS o más vías (tramos compartidos). */
fun sharedSegmentKeys(lines: List<TopoLineData>): Set<String> {
    val count = HashMap<String, Int>()
    lines.forEach { line ->
        // Un set por vía: que una vía repita su propio segmento no cuenta.
        val own = HashSet<String>()
        line.points.zipWithNext().forEach { (a, b) -> own.add(segmentKey(a, b)) }
        own.forEach { k -> count[k] = (count[k] ?: 0) + 1 }
    }
    return count.filterValues { it >= 2 }.keys
}

/**
 * IMÁN del editor: ajusta un trazo recién dibujado a las vías ya existentes.
 * Cada punto del trazo que caiga a menos de [threshold] (coords normalizadas)
 * de un VÉRTICE de otra vía se sustituye por ese vértice exacto; y entre dos
 * puntos consecutivos imantados a la MISMA vía se insertan sus vértices
 * intermedios (el dedo va más rápido que los vértices). Resultado: el tramo
 * común queda con LOS MISMOS puntos que la otra vía → `sharedSegmentKeys` lo
 * detecta con exactitud. Los puntos lejos de cualquier vía no se tocan.
 */
fun magnetizeStroke(
    drawn: List<Pair<Float, Float>>,
    others: List<List<Pair<Float, Float>>>,
    threshold: Float = 0.02f
): List<Pair<Float, Float>> {
    if (drawn.isEmpty() || others.isEmpty()) return drawn
    // (índice de vía, índice de vértice) del vértice más cercano bajo umbral.
    fun snap(p: Pair<Float, Float>): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var bestD = threshold * threshold
        others.forEachIndexed { li, pts ->
            pts.forEachIndexed { vi, v ->
                val dx = p.first - v.first
                val dy = p.second - v.second
                val d = dx * dx + dy * dy
                if (d < bestD) { bestD = d; best = li to vi }
            }
        }
        return best
    }

    data class Node(val point: Pair<Float, Float>, val snapped: Pair<Int, Int>?)
    val nodes = drawn.map { p ->
        val s = snap(p)
        Node(if (s != null) others[s.first][s.second] else p, s)
    }

    val out = mutableListOf<Pair<Float, Float>>()
    nodes.forEachIndexed { i, n ->
        if (i > 0) {
            val prev = nodes[i - 1]
            // Dos puntos seguidos imantados a la MISMA vía: insertar los
            // vértices intermedios de esa vía (en el sentido del trazo).
            val a = prev.snapped; val b = n.snapped
            if (a != null && b != null && a.first == b.first && kotlin.math.abs(b.second - a.second) > 1) {
                val pts = others[a.first]
                val range = if (b.second > a.second) (a.second + 1) until b.second
                else (a.second - 1) downTo (b.second + 1)
                for (vi in range) out.add(pts[vi])
            }
        }
        out.add(n.point)
    }
    // Sin puntos duplicados consecutivos (el imán puede colapsar varios en uno).
    return out.filterIndexed { i, p -> i == 0 || p != out[i - 1] }
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
    // Tramos compartidos entre vías → color propio (ver sharedSegmentKeys).
    val shared = sharedSegmentKeys(lines)

    lines.forEachIndexed { idx, line ->
        if (line.points.isEmpty()) return@forEachIndexed
        val (strokeArgb, dashed, dark) = gradeArgb(line.grade)
        val pts = line.points.map { (nx, ny) -> nx * w to ny * h }
        val textArgb = if (dark) 0xFF000000L else 0xFFFFFFFFL

        // La polilínea se trocea en RACHAS de segmentos propios/compartidos y
        // cada racha se pinta de su color (grado o SHARED_SEGMENT_ARGB). Con
        // una sola racha (lo normal) es idéntico a antes.
        val runs = mutableListOf<Pair<List<Pair<Float, Float>>, Boolean>>()
        if (line.points.size < 2) {
            runs.add(pts to false)
        } else {
            var runStart = 0
            var runShared = segmentKey(line.points[0], line.points[1]) in shared
            for (i in 1 until line.points.size - 1) {
                val segShared = segmentKey(line.points[i], line.points[i + 1]) in shared
                if (segShared != runShared) {
                    runs.add(pts.subList(runStart, i + 1).toList() to runShared)
                    runStart = i
                    runShared = segShared
                }
            }
            runs.add(pts.subList(runStart, pts.size).toList() to runShared)
        }
        runs.forEach { (runPts, isShared) ->
            val color = if (isShared) SHARED_SEGMENT_ARGB else strokeArgb
            // Outline para que líneas blancas sean visibles sobre cualquier foto
            if (dark && !isShared) {
                ops += DrawOp.LinePath(runPts, 0xCC000000L, line.strokeWidthPx + 4f, dashed)
            }
            ops += DrawOp.LinePath(runPts, color, line.strokeWidthPx, dashed && !isShared)
        }

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
