package com.meteomontana.android.domain.util

/**
 * Saber qué vía has tocado en la foto.
 *
 * Es lo que hace posible el modo FOCO: en una piedra con muchas vías —el muro
 * de Teverga tiene 13— señalar una con el dedo es la única forma cómoda de
 * aislarla. Aquí solo está la geometría; qué hacer con la vía elegida lo decide
 * cada pantalla.
 *
 * Todo en coordenadas de la foto (0..1), como el resto del topo.
 */

/** Distancia al cuadrado de un punto a un tramo, con la proyección acotada. */
private fun dist2ToSegment(
    px: Float, py: Float,
    ax: Float, ay: Float, bx: Float, by: Float
): Float {
    val abx = bx - ax
    val aby = by - ay
    val len2 = abx * abx + aby * aby
    val t = if (len2 < 1e-12f) 0f
    else (((px - ax) * abx + (py - ay) * aby) / len2).coerceIn(0f, 1f)
    val qx = ax + t * abx
    val qy = ay + t * aby
    val dx = px - qx
    val dy = py - qy
    return dx * dx + dy * dy
}

/**
 * Distancia de un punto a una polilínea. Se mide contra los TRAMOS, no contra
 * los vértices: si no, tocar en mitad de una vía larga no la seleccionaría.
 */
fun distanceToPolyline(px: Float, py: Float, points: List<Pair<Float, Float>>): Float {
    if (points.isEmpty()) return Float.MAX_VALUE
    if (points.size == 1) {
        val dx = px - points[0].first
        val dy = py - points[0].second
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
    var best = Float.MAX_VALUE
    for (i in 0 until points.size - 1) {
        val d = dist2ToSegment(px, py, points[i].first, points[i].second,
            points[i + 1].first, points[i + 1].second)
        if (d < best) best = d
    }
    return kotlin.math.sqrt(best)
}

/**
 * La vía más cercana al punto tocado, o null si no hay ninguna lo bastante
 * cerca. El umbral es generoso a propósito: el dedo es gordo y en un muro con
 * vías juntas es mejor acertar la de al lado que no acertar ninguna — siempre
 * se puede tocar otra vez, y tocar fuera apaga el foco.
 *
 * @param maxDistance en coordenadas de foto; 0.05 ≈ 5% del ancho.
 */
fun nearestLineIndex(
    lines: List<List<Pair<Float, Float>>>,
    px: Float,
    py: Float,
    maxDistance: Float = 0.05f
): Int? {
    var best: Int? = null
    var bestD = maxDistance
    lines.forEachIndexed { i, pts ->
        if (pts.isNotEmpty()) {
            val d = distanceToPolyline(px, py, pts)
            if (d < bestD) { bestD = d; best = i }
        }
    }
    return best
}
