package com.meteomontana.android.domain.usecase.gym

/**
 * «Hoy la roca está mojada cerca de ti · ¿Rocódromo?»: cuándo avisar en Escuelas. Se mira la lista de escuelas que
 * entran en el filtro de distancia y su `dryRock` de hoy (el criterio de roca mojada de siempre; sin umbrales
 * nuevos). Lógica pura, igual en iOS y Android.
 */
object WetRockAdvisor {
    /** Con pocas escuelas conocidas un solo dato no decide: hacen falta al menos 3 (o todas, si hay menos). */
    const val MIN_SAMPLE = 3

    /**
     * [dryFlags]: una entrada por escuela cercana; `true` seca, `false` mojada, `null` aún sin dato (cargando).
     * Avisa si hay muestra suficiente y MÁS de la mitad de las conocidas están mojadas.
     */
    fun isMostlyWet(dryFlags: List<Boolean?>): Boolean {
        if (dryFlags.isEmpty()) return false
        val known = dryFlags.filterNotNull()
        if (known.isEmpty() || known.size < minOf(MIN_SAMPLE, dryFlags.size)) return false
        return known.count { !it } * 2 > known.size
    }
}
