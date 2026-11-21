package com.meteomontana.android.domain.model.gym

/**
 * Orden de las categorias de la pantalla de Ascensiones (Bloques, Vias, Boards) que elige cada persona; «Todo» va siempre
 * primero y no se mueve. Se guarda solo en el movil como texto («BOARDS,BLOCKS,ROUTES»); si falta o esta roto, vale el orden
 * de siempre y lo que no aparezca se anade al final.
 */
object AscentTabOrder {
    val default: List<AscentFilter> = listOf(AscentFilter.BLOCKS, AscentFilter.ROUTES, AscentFilter.BOARDS)

    fun parse(saved: String?): List<AscentFilter> {
        val fromSaved = saved.orEmpty().split(",").mapNotNull { name -> default.firstOrNull { it.name == name.trim() } }.distinct()
        return fromSaved + default.filter { it !in fromSaved }
    }

    fun serialize(order: List<AscentFilter>): String = order.joinToString(",") { it.name }

    /** Mueve la categoria de [index] una posicion arriba (delta -1) o abajo (+1); si no se puede, no cambia nada. */
    fun move(order: List<AscentFilter>, index: Int, delta: Int): List<AscentFilter> {
        val to = index + delta
        if (index !in order.indices || to !in order.indices) return order
        return order.toMutableList().also { val item = it.removeAt(index); it.add(to, item) }
    }

    /** Pestañas de la pantalla: «Todo» y luego las categorias en el orden elegido. */
    fun tabs(order: List<AscentFilter>): List<AscentFilter> = listOf(AscentFilter.ALL) + order
}
