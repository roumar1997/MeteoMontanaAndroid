package com.meteomontana.android.domain.util

/**
 * Etiqueta del chip con el que se elige qué vía dibujar en el editor de topos.
 *
 * Existe porque las dos apps la componían por su cuenta y habían divergido:
 * iOS ponía solo `1 · 7a` —con tres vías del mismo grado no hay forma de saber
 * cuál es cuál— y Android ponía el nombre pero perdía el número, que es lo que
 * te enlaza con el badge dibujado sobre la roca. Ninguna enseñaba la variante,
 * que es justo lo que distingue dos vías homónimas.
 *
 * El número NO se quita nunca: es el que sale pintado en la foto.
 */
object TopoChipLabel {

    /** A partir de aquí el nombre se recorta: los chips van en una tira. */
    const val MAX_NOMBRE = 18

    /**
     * Etiqueta de la vía [index] (base 0; se muestra en base 1).
     *
     * Formato: `2 · La ola (directa) · 7a`. Lo que falte se omite sin dejar
     * separadores sueltos — una vía recién creada aún no tiene nombre ni grado
     * y debe quedar simplemente `3`.
     */
    fun of(index: Int, name: String?, variant: String? = null, grade: String? = null): String {
        val partes = mutableListOf("${index + 1}")
        nombreVisible(name, variant)?.let { partes.add(it) }
        grade?.trim()?.takeIf { it.isNotEmpty() }?.let { partes.add(it) }
        return partes.joinToString(" · ")
    }

    /** "La ola (directa)", recortado si no cabe. null si no hay nombre. */
    private fun nombreVisible(name: String?, variant: String?): String? {
        val n = name?.trim().orEmpty()
        if (n.isEmpty()) return null
        val v = variant?.trim().orEmpty()
        val completo = if (v.isEmpty()) n else "$n ($v)"
        return if (completo.length <= MAX_NOMBRE) completo
        else completo.take(MAX_NOMBRE - 1).trimEnd() + "…"
    }
}
