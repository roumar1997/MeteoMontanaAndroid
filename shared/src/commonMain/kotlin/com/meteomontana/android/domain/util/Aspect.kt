package com.meteomontana.android.domain.util

import kotlin.math.roundToInt

/**
 * Orientación de una pared: los ocho puntos cardinales y su relación con los
 * grados de la brújula.
 *
 * La lista de los ocho estaba copiada en cinco pantallas (votar orientación,
 * filtrar por orientación, crear piedra... en las dos apps). Aquí hay una sola,
 * y con ella la conversión desde el rumbo del móvil, que es lo que permite
 * enseñar la brújula mientras eliges.
 *
 * El orden es el de la rosa: N, NE, E, SE, S, SO, O, NO. No se toca — las
 * pantallas de votos lo usan como orden de presentación.
 */
object Aspect {

    val ALL = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")

    /** Cuántos grados abarca cada punto cardinal (360 / 8). */
    const val SECTOR = 45f

    /**
     * Punto cardinal de un rumbo en grados (0 = norte, sentido horario).
     *
     * Cada punto abarca su sector CENTRADO en su dirección exacta: el norte va
     * de 337,5° a 22,5°, no de 0° a 45°. Si se partiera en trozos de 45 desde
     * cero, 350° —que es norte de toda la vida— saldría como NO.
     *
     * Acepta cualquier valor: los negativos y los mayores de 360 se normalizan,
     * porque los sensores de los móviles devuelven de todo.
     */
    fun fromDegrees(degrees: Float): String {
        val normalizado = ((degrees % 360f) + 360f) % 360f
        val indice = ((normalizado + SECTOR / 2f) / SECTOR).toInt() % ALL.size
        return ALL[indice]
    }

    /** Grados exactos de un punto cardinal. null si no es uno de los ocho. */
    fun degreesOf(aspect: String): Float? {
        val i = ALL.indexOf(aspect.trim().uppercase())
        return if (i < 0) null else i * SECTOR
    }

    /**
     * Rumbo redondeado para enseñarlo junto a la brújula ("52°").
     *
     * Se muestra el número además del punto cardinal porque la brújula del móvil
     * se descalibra con facilidad —mochilas, mosquetones, hierro cerca— y ver el
     * grado ayuda a desconfiar cuando salta.
     */
    fun degreesLabel(degrees: Float): String {
        val normalizado = ((degrees % 360f) + 360f) % 360f
        return "${normalizado.roundToInt() % 360}°"
    }
}
