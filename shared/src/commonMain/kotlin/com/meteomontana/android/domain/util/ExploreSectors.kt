package com.meteomontana.android.domain.util

import com.meteomontana.android.domain.model.LineSearchHit

/** Resultados de una escuela de Vías/Bloques que caen en un mismo sector (`name == null`: sin sector). */
data class SectorBucket(val name: String?, val hits: List<LineSearchHit>)

/** Lógica pura (compartida con iOS) del agrupado por sectores del buscador de Vías/Bloques. */
object ExploreSectors {
    /**
     * Reparte los resultados de UNA escuela por sector, en el orden en que aparece cada sector. Con menos de dos
     * sectores distintos devuelve `null`: no tiene sentido plegar una sola caja.
     */
    fun split(hits: List<LineSearchHit>): List<SectorBucket>? {
        val keys = hits.map { it.sectorName?.trim()?.takeIf { s -> s.isNotEmpty() } }
        if (keys.distinct().size < 2) return null
        val order = keys.distinct()
        return order.map { key -> SectorBucket(key, hits.filterIndexed { i, _ -> keys[i] == key }) }
    }

    /** `true` si todos los resultados son bloques (por su propia modalidad, o por el estilo de la escuela si no la traen). */
    fun allBoulders(hits: List<LineSearchHit>, schoolStyle: String?): Boolean {
        if (hits.isEmpty()) return false
        val own = hits.mapNotNull { it.discipline }
        if (own.size == hits.size) return own.all { it.equals("BOULDER", ignoreCase = true) }
        val kind = SchoolKind.from(schoolStyle)
        return kind.hasBoulders && !kind.hasRoutes
    }

    /** `true` si todos los resultados son vías. */
    fun allRoutes(hits: List<LineSearchHit>, schoolStyle: String?): Boolean {
        if (hits.isEmpty()) return false
        val own = hits.mapNotNull { it.discipline }
        if (own.size == hits.size) return own.all { it.equals("ROUTE", ignoreCase = true) }
        val kind = SchoolKind.from(schoolStyle)
        return kind.hasRoutes && !kind.hasBoulders
    }
}
