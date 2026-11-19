package com.meteomontana.android.ui.screens.gyms

/** Un marcador suelto en pantalla (píxeles): su base está en ([x], [y]) y su nombre mide [nameChars] letras. */
internal data class LabelCandidate(
    val id: String,
    val x: Float,
    val y: Float,
    val nameChars: Int,
    /** El seleccionado: su nombre se pinta siempre que se pueda, antes que los demás. */
    val priority: Boolean = false
)

/** Reparto de nombres bajo los marcadores del mapa (lógica pura: se prueba sin móvil). */
internal object GymLabelPlanner {

    /** Zoom mínimo para enseñar nombres; por debajo mandan los grupos y los puntos sueltos sin texto. */
    const val MIN_ZOOM = 10.5

    private const val CIRCLE_DP = 38f
    private const val LABEL_H_DP = 15f
    private const val CHAR_DP = 6.4f
    private const val MAX_CHARS = 18

    /** Alto extra, en dp, que añade el nombre al bitmap del marcador. */
    const val LABEL_EXTRA_DP = LABEL_H_DP

    /** Ancho estimado del nombre en dp (recortado a [MAX_CHARS] letras). */
    fun labelWidthDp(chars: Int): Float = minOf(chars, MAX_CHARS) * CHAR_DP + 8f

    /**
     * Ids que llevan nombre. Un nombre se pinta solo si su caja no pisa el círculo de ningún otro marcador ni el
     * nombre ya colocado de otro: así nunca se montan unos sobre otros. Prioritarios primero, luego por id.
     */
    fun plan(candidates: List<LabelCandidate>, density: Float): Set<String> {
        val k = density
        val circles = candidates.map { c -> c.id to floatArrayOf(c.x - CIRCLE_DP / 2 * k, c.y - CIRCLE_DP * k, c.x + CIRCLE_DP / 2 * k, c.y) }
        val placed = mutableListOf<FloatArray>()
        val result = mutableSetOf<String>()
        candidates.sortedWith(compareByDescending<LabelCandidate> { it.priority }.thenBy { it.id }).forEach { c ->
            if (c.nameChars <= 0) return@forEach
            val half = labelWidthDp(c.nameChars) / 2 * k
            val box = floatArrayOf(c.x - half, c.y, c.x + half, c.y + LABEL_H_DP * k)
            val clear = circles.none { (id, r) -> id != c.id && overlaps(box, r) } && placed.none { overlaps(box, it) }
            if (clear) { placed += box; result += c.id }
        }
        return result
    }

    private fun overlaps(a: FloatArray, b: FloatArray) = a[0] < b[2] && a[2] > b[0] && a[1] < b[3] && a[3] > b[1]
}
