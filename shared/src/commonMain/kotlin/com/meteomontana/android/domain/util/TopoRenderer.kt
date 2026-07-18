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
// existente y COPIA sus puntos), el tramo común se pinta a FRANJAS alternas
// con los colores de las vías que lo comparten (cada vía pinta su color a
// guiones con fase distinta → se intercalan solos). La detección es por
// IGUALDAD de segmentos (mismos dos vértices, en cualquier orden): como el
// imán copia los puntos exactos, no hay que "adivinar" cercanías al pintar.

/** Largo de cada franja del tramo compartido, en px del canvas. */
const val SHARED_STRIPE_PX = 22f

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

/** Segmento compartido → índices (ordenados) de las vías que lo comparten.
 *  Solo entradas con 2+ vías. */
fun sharedSegmentLines(lines: List<TopoLineData>): Map<String, List<Int>> {
    val byKey = HashMap<String, MutableSet<Int>>()
    lines.forEachIndexed { idx, line ->
        line.points.zipWithNext().forEach { (a, b) ->
            byKey.getOrPut(segmentKey(a, b)) { mutableSetOf() }.add(idx)
        }
    }
    return byKey.filterValues { it.size >= 2 }.mapValues { it.value.sorted() }
}

/** Claves de los segmentos presentes en DOS o más vías (tramos compartidos). */
fun sharedSegmentKeys(lines: List<TopoLineData>): Set<String> =
    sharedSegmentLines(lines).keys

/**
 * SUAVIZADO del trazo a mano (Douglas-Peucker): quita el temblor del pulso y
 * deja una línea limpia con pocos vértices (como las de las guías). [epsilon]
 * en coords normalizadas (~0.006 = suave sin comerse curvas reales).
 */
fun simplifyStroke(
    points: List<Pair<Float, Float>>,
    epsilon: Float = 0.006f
): List<Pair<Float, Float>> {
    if (points.size <= 2) return points
    fun perpDist(p: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
        val dx = b.first - a.first; val dy = b.second - a.second
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        if (len < 1e-9f) {
            val ex = p.first - a.first; val ey = p.second - a.second
            return kotlin.math.sqrt(ex * ex + ey * ey)
        }
        return kotlin.math.abs(dy * p.first - dx * p.second + b.first * a.second - b.second * a.first) / len
    }
    fun dp(from: Int, to: Int, keep: BooleanArray) {
        var maxD = 0f; var maxI = -1
        for (i in from + 1 until to) {
            val d = perpDist(points[i], points[from], points[to])
            if (d > maxD) { maxD = d; maxI = i }
        }
        if (maxD > epsilon && maxI > 0) {
            keep[maxI] = true
            dp(from, maxI, keep); dp(maxI, to, keep)
        }
    }
    val keep = BooleanArray(points.size)
    keep[0] = true; keep[points.size - 1] = true
    dp(0, points.size - 1, keep)
    return points.filterIndexed { i, _ -> keep[i] }
}

/**
 * ABANICO de badges: cuando varias vías empiezan/acaban en el MISMO punto,
 * sus badges se despliegan lado a lado en vez de apilarse. Devuelve, para
 * cada vía, el desplazamiento X en px de su badge (0 si no hay coincidencia).
 * [anchors] = punto (normalizado) del badge de cada vía, null si no tiene.
 */
fun fanOffsets(anchors: List<Pair<Float, Float>?>, spacingPx: Float): List<Float> {
    val groups = HashMap<String, MutableList<Int>>()
    anchors.forEachIndexed { idx, p ->
        if (p != null) groups.getOrPut(pointKey(p)) { mutableListOf() }.add(idx)
    }
    val out = MutableList(anchors.size) { 0f }
    groups.values.forEach { members ->
        if (members.size > 1) {
            members.forEachIndexed { k, idx ->
                out[idx] = (k - (members.size - 1) / 2f) * spacingPx
            }
        }
    }
    return out
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
    threshold: Float = 0.04f
): List<Pair<Float, Float>> {
    if (drawn.isEmpty() || others.isEmpty()) return drawn
    // (vía, vértice) al que imantarse: el punto se compara contra CUALQUIER
    // TRAMO de las otras vías (no solo sus vértices — antes era casi imposible
    // acertar con el dedo) y, si cae bajo el umbral, se pega al vértice más
    // cercano de ese tramo. Así el tramo común sigue siendo EXACTO.
    fun snap(p: Pair<Float, Float>): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var bestD = threshold * threshold
        others.forEachIndexed { li, pts ->
            for (si in 0 until pts.size - 1) {
                val a = pts[si]; val b = pts[si + 1]
                // Distancia punto→segmento (proyección acotada 0..1).
                val abx = b.first - a.first; val aby = b.second - a.second
                val len2 = abx * abx + aby * aby
                val t = if (len2 < 1e-12f) 0f else
                    (((p.first - a.first) * abx + (p.second - a.second) * aby) / len2)
                        .coerceIn(0f, 1f)
                val qx = a.first + t * abx; val qy = a.second + t * aby
                val dx = p.first - qx; val dy = p.second - qy
                val d = dx * dx + dy * dy
                if (d < bestD) {
                    bestD = d
                    // Vértice más cercano del tramo (para compartir EXACTO).
                    best = li to (if (t < 0.5f) si else si + 1)
                }
            }
            // Vías de un solo punto: comparar contra ese vértice suelto.
            if (pts.size == 1) {
                val dx = p.first - pts[0].first; val dy = p.second - pts[0].second
                val d = dx * dx + dy * dy
                if (d < bestD) { bestD = d; best = li to 0 }
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
    startTextPx: Pair<Float, Float> = 18f to 6f,
    /** Guion de TODAS las líneas (on, off) en px — estilo guía: discontinuas
     *  para no tapar la roca. El caller lo escala (density / canvas 1080). */
    dashPx: Pair<Float, Float> = 12f to 9f,
    /** Largo de cada franja de tramo compartido, en px (escalar como dashPx). */
    stripePx: Float = SHARED_STRIPE_PX
): List<DrawOp> {
    val ops = mutableListOf<DrawOp>()
    // Tramos compartidos: segmento → vías que lo comparten (franjas alternas).
    val shared = sharedSegmentLines(lines)
    // Abanico de badges: cuando varios inicios/finales coinciden en el mismo
    // punto, cada badge se desplaza en X para no taparse.
    val startFan = fanOffsets(
        lines.map { it.points.firstOrNull() }, badgeR.first * 2f + 4f)
    val endFan = fanOffsets(
        lines.map { it.points.lastOrNull() }, startR.first * 2f + 4f)

    lines.forEachIndexed { idx, line ->
        if (line.points.isEmpty()) return@forEachIndexed
        val (strokeArgb, dashed, dark) = gradeArgb(line.grade)
        val pts = line.points.map { (nx, ny) -> nx * w to ny * h }
        val textArgb = if (dark) 0xFF000000L else 0xFFFFFFFFL

        // La polilínea se trocea en RACHAS: segmentos propios (color de grado,
        // como siempre) y segmentos compartidos (FRANJAS: esta vía pinta su
        // color a guiones con fase = su posición entre las vías del tramo; las
        // demás rellenan los huecos → colores alternos, estilo guía).
        data class Run(val pts: List<Pair<Float, Float>>, val sharers: List<Int>)
        val runs = mutableListOf<Run>()
        if (line.points.size < 2) {
            runs.add(Run(pts, emptyList()))
        } else {
            fun sharersOf(i: Int): List<Int> =
                shared[segmentKey(line.points[i], line.points[i + 1])] ?: emptyList()
            var runStart = 0
            var runSharers = sharersOf(0)
            for (i in 1 until line.points.size - 1) {
                val s = sharersOf(i)
                if (s != runSharers) {
                    runs.add(Run(pts.subList(runStart, i + 1).toList(), runSharers))
                    runStart = i
                    runSharers = s
                }
            }
            runs.add(Run(pts.subList(runStart, pts.size).toList(), runSharers))
        }
        runs.forEach { run ->
            val isShared = run.sharers.size >= 2
            if (!isShared) {
                // ESTILO GUÍA: todas las líneas discontinuas (rayitas) — un
                // trazo macizo tapa la roca (feedback de Rodrigo con la guía
                // de La Pedriza en la mano).
                // Outline para que líneas blancas sean visibles sobre la foto
                if (dark) {
                    ops += DrawOp.LinePath(run.pts, 0xCC000000L, line.strokeWidthPx + 4f,
                        dashed = true, dashPattern = dashPx)
                }
                ops += DrawOp.LinePath(run.pts, strokeArgb, line.strokeWidthPx,
                    dashed = true, dashPattern = dashPx)
            } else {
                // Franja: guion de largo stripePx, hueco = franjas de las
                // otras vías, fase = mi posición en el grupo.
                val n = run.sharers.size
                val k = run.sharers.indexOf(idx).coerceAtLeast(0)
                ops += DrawOp.LinePath(
                    run.pts, strokeArgb, line.strokeWidthPx,
                    dashed = true,
                    dashPattern = stripePx to stripePx * (n - 1),
                    dashPhase = k * stripePx
                )
            }
        }

        // Badge numérico en el punto de inicio (desplazado si hay abanico)
        val (fx0, fy) = pts.first()
        val fx = fx0 + startFan[idx]
        ops += DrawOp.FilledCircle(fx, fy, badgeR.first, 0xFFFFFFFFL)
        ops += DrawOp.FilledCircle(fx, fy, badgeR.second, strokeArgb)
        ops += DrawOp.TextLabel(fx, fy, "${idx + 1}", textArgb, badgeTextPx.first, bold = true, badgeTextPx.second)

        // Badge de tipo de inicio en el punto final (desplazado si hay abanico)
        val label = when (line.startType?.uppercase()) {
            "PIE", "STAND"  -> "PIE"
            "SIT"           -> "SIT"
            "SEMI"          -> "SEM"
            "LANCE", "JUMP" -> "LAN"
            "TRAV"          -> "TRV"
            else            -> null
        }
        if (label != null) {
            val (lx0, ly) = pts.last()
            val lx = lx0 + endFan[idx]
            val haloArgb = if (dark) 0xFF000000L else 0xFFFFFFFFL
            ops += DrawOp.FilledCircle(lx, ly, startR.first, haloArgb)
            ops += DrawOp.FilledCircle(lx, ly, startR.second, strokeArgb)
            if (dark) ops += DrawOp.CircleStroke(lx, ly, startR.second, 0xFF000000L, 2f)
            ops += DrawOp.TextLabel(lx, ly, label, textArgb, startTextPx.first, bold = true, startTextPx.second)
        }
    }
    return ops
}
