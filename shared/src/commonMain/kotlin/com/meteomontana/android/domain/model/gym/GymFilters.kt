package com.meteomontana.android.domain.model.gym

import com.meteomontana.android.domain.util.Geo
import kotlinx.datetime.Instant

/** Que capa enseña el selector: "Rocodromos" (comerciales y municipales) o "Muros gratis". */
enum class GymMode { ROCODROMOS, FREE_WALLS }

enum class GymSort { DISTANCE, NAME }

/** Punto desde el que se mide la distancia (ubicacion del usuario). */
data class GymOrigin(val lat: Double, val lon: Double)

/**
 * Filtros de la lista y del mapa. Todo se filtra en el movil sobre el catalogo entero (349 fichas).
 * Una ficha con el dato desconocido NO entra cuando el filtro lo exige: no se promete lo que no se sabe.
 */
data class GymFilters(
    val mode: GymMode = GymMode.ROCODROMOS,
    /** "Gratis": muro gratuito o precio de entrada 0. */
    val onlyFree: Boolean = false,
    val boulder: Boolean = false,
    val rope: Boolean = false,
    val openNow: Boolean = false,
    val onlyFavorites: Boolean = false,
    val chain: String? = null,
    val maxDistanceKm: Double? = null,
    val query: String = "",
    val sort: GymSort = GymSort.DISTANCE
)

/** Una ficha con la distancia al origen (null si no hay origen). */
data class GymListItem(val gym: Gym, val distanceKm: Double?)

object GymFilterEngine {

    /**
     * @param favoriteIds ids favoritos del usuario (para [GymFilters.onlyFavorites]).
     * @param now instante para "abierto ahora".
     */
    fun apply(
        gyms: List<Gym>,
        filters: GymFilters,
        origin: GymOrigin? = null,
        favoriteIds: Set<Long> = emptySet(),
        now: Instant? = null
    ): List<GymListItem> {
        val q = filters.query.trim()
        val items = gyms.asSequence()
            .filter { !it.hidden }
            .filter { matchesMode(it, filters.mode) }
            .filter { !filters.onlyFree || it.isFree }
            .filter { !filters.boulder || it.discipline.hasBoulder }
            .filter { !filters.rope || it.discipline.hasRope }
            .filter { !filters.onlyFavorites || it.id in favoriteIds }
            .filter { filters.chain == null || it.chain.equals(filters.chain, ignoreCase = true) }
            .filter { q.isEmpty() || matchesQuery(it, q) }
            .filter { !filters.openNow || (now != null && it.isOpenAt(now) == true) }
            .map { GymListItem(it, origin?.let { o -> Geo.haversineKm(o.lat, o.lon, it.lat, it.lon) }) }
            .filter { item ->
                val max = filters.maxDistanceKm
                max == null || item.distanceKm == null || item.distanceKm <= max
            }
            .toList()
        return when {
            filters.sort == GymSort.DISTANCE && origin != null ->
                items.sortedWith(compareBy<GymListItem> { it.distanceKm }.thenBy { it.gym.name.lowercase() })
            else -> items.sortedBy { it.gym.name.lowercase() }
        }
    }

    /** Cadenas con al menos una ficha en el modo dado, para el chip de cadena (orden alfabetico). */
    fun chains(gyms: List<Gym>, mode: GymMode): List<String> =
        gyms.filter { !it.hidden && matchesMode(it, mode) }
            .mapNotNull { it.chain?.trim()?.takeIf(String::isNotEmpty) }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

    private fun matchesMode(g: Gym, mode: GymMode) = when (mode) {
        GymMode.ROCODROMOS -> g.kind != GymKind.FREE
        GymMode.FREE_WALLS -> g.kind == GymKind.FREE
    }

    private fun matchesQuery(g: Gym, q: String): Boolean {
        val needle = fold(q)
        return fold(g.name).contains(needle) || fold(g.city).contains(needle) ||
            fold(g.province).contains(needle) || (g.chain?.let { fold(it).contains(needle) } ?: false)
    }

    /** Minusculas y sin tildes, para que "cadiz" encuentre "Cádiz". */
    private fun fold(s: String): String = buildString(s.length) {
        for (c in s.lowercase()) append(
            when (c) { 'á' -> 'a'; 'é' -> 'e'; 'í' -> 'i'; 'ó' -> 'o'; 'ú', 'ü' -> 'u'; else -> c }
        )
    }
}
