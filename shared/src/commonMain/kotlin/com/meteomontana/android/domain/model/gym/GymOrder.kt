package com.meteomontana.android.domain.model.gym

/**
 * Orden de los rocodromos en Ascensiones y en Estadisticas > Rocodromo > «Por rocodromo». Por defecto, los FAVORITOS
 * primero (y dentro de cada grupo, el orden que ya traen: mas ascensiones primero); si la persona los ha ordenado a mano,
 * manda su orden. Se guarda como ids separados por comas («5,3,9»), en el movil y en el servidor.
 */
object GymOrder {
    fun parse(saved: String?): List<Long> =
        saved.orEmpty().split(",").mapNotNull { it.trim().toLongOrNull() }.filter { it > 0 }.distinct()

    fun serialize(ids: List<Long>): String = ids.joinToString(",")

    /** Los de [saved] primero y en ese orden (solo los que existen en [gyms]); el resto, favoritos antes y sin mover entre si. */
    fun sort(gyms: List<GymSummaryGym>, saved: List<Long>, favorites: Set<Long>): List<GymSummaryGym> {
        val byId = gyms.associateBy { it.gymId }
        val chosen = saved.mapNotNull { byId[it] }
        val chosenIds = chosen.map { it.gymId }.toSet()
        val rest = gyms.filter { it.gymId !in chosenIds }
        return chosen + rest.filter { it.gymId in favorites } + rest.filter { it.gymId !in favorites }
    }

    /** Mueve el rocodromo de [index] una posicion arriba (delta -1) o abajo (+1); si no se puede, no cambia nada. */
    fun move(ids: List<Long>, index: Int, delta: Int): List<Long> {
        val to = index + delta
        if (index !in ids.indices || to !in ids.indices) return ids
        return ids.toMutableList().also { val item = it.removeAt(index); it.add(to, item) }
    }
}
