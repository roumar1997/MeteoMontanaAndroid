package com.meteomontana.android.domain.util

/**
 * Grados DOBLES ("7a/7a+"): una vía cuya dificultad no está clara se puede
 * apuntar como rango entre dos grados consecutivos, tal y como se hace en las
 * guías de papel (Álvaro, 2026-08-24).
 *
 * El grado sigue siendo un String libre en `BlockLine` — no hay cambio de datos
 * ni migración. Lo único que hace falta es que TODO lo que interpreta un grado
 * (color, orden, filtros, estadísticas) lea el grado BASE, es decir el primero
 * del rango. Puro Kotlin: Android e iOS comparten exactamente estas reglas.
 */
object GradeRange {

    /** Separador del rango. Un rango tiene siempre exactamente dos grados. */
    const val SEP = "/"

    /**
     * Grado con el que se colorea/ordena una vía: el primero del rango.
     * "7a/7a+" → "7A"; "6b" → "6B"; null/vacío → null.
     */
    fun base(grade: String?): String? =
        grade?.split(SEP)?.firstOrNull()?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }

    /** Los grados que componen la selección actual (1 o 2, en orden). */
    fun parts(grade: String?): List<String> =
        (grade ?: "").split(SEP).map { it.trim().uppercase() }.filter { it.isNotEmpty() }

    /** ¿Está [candidate] seleccionado dentro de [current] (grado suelto o rango)? */
    fun contains(current: String?, candidate: String): Boolean =
        candidate.trim().uppercase() in parts(current)

    /**
     * Regla de toque en la rejilla de grados:
     *  - nada seleccionado → queda ese grado suelto.
     *  - uno seleccionado y tocas OTRO → rango de los dos, ordenados de fácil a
     *    difícil ("7a/7a+", nunca "7a+/7a").
     *  - tocas uno YA seleccionado → se quita (si era rango, queda el otro solo).
     *  - rango completo y tocas un TERCERO → se empieza de nuevo con ese.
     */
    fun toggle(current: String?, tapped: String): String? {
        val t = tapped.trim().uppercase()
        if (t.isEmpty()) return current
        val p = parts(current)
        return when {
            t in p -> (p - t).firstOrNull()
            p.isEmpty() -> t
            p.size == 1 -> listOf(p[0], t).sortedBy { gradeScore(it) ?: 0 }.joinToString(SEP)
            else -> t
        }
    }
}
