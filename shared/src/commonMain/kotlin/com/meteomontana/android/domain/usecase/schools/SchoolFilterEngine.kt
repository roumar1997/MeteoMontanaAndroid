package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.util.Geo

/**
 * Lógica PURA de filtrado y orden del catálogo de escuelas. Extraída de
 * SchoolListViewModel (526 líneas) para poder testearla sin Android y compartir
 * el criterio con iOS. No conoce tipos de UI: recibe primitivos (hexagonal). El
 * ViewModel sigue decidiendo QUÉ score usar (hoy vs tramo); aquí solo se ordena.
 */
object SchoolFilterEngine {

    /** Búsqueda por texto: nombre o ubicación contienen la query (sin distinguir
     *  mayúsculas). Blanco → devuelve la lista tal cual. */
    fun filterByQuery(schools: List<School>, query: String): List<School> {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return schools
        return schools.filter {
            it.name.lowercase().contains(needle) ||
                it.location?.lowercase()?.contains(needle) == true
        }
    }

    /**
     * Filtro de exploración (estilo/roca/distancia/favoritos). REGLA: si [query]
     * no está en blanco, el texto MANDA sobre todo lo demás (busca en el catálogo
     * completo ignorando distancia/estilo/roca/favoritos) — así "Albarracín" sale
     * aunque esté fuera del radio.
     */
    fun filter(
        schools: List<School>,
        query: String,
        styleApiValue: String?,
        rockTypes: List<String>,
        maxDistanceKm: Double?,
        onlyFavorites: Boolean,
        favoriteIds: Set<String>,
        userLat: Double,
        userLon: Double
    ): List<School> {
        if (query.isNotBlank()) return filterByQuery(schools, query)
        var list = schools
            .filter { styleApiValue == null || styleApiValue.equals(it.style, ignoreCase = true) }
            .filter { rockTypes.isEmpty() || rockTypes.any { r -> r.equals(it.rockType, ignoreCase = true) } }
            .filter { maxDistanceKm == null || Geo.haversineKm(userLat, userLon, it.lat, it.lon) <= maxDistanceKm }
        if (onlyFavorites) list = list.filter { it.id in favoriteIds }
        return list
    }

    /** Orden por distancia ascendente al usuario. */
    fun sortByDistance(schools: List<School>, userLat: Double, userLon: Double): List<School> =
        schools.sortedBy { Geo.haversineKm(userLat, userLon, it.lat, it.lon) }

    /** Orden por score descendente; [scoreOf] resuelve el score de cada id (hoy o
     *  tramo — lo decide el caller). Sin score → al final (convención: -1). */
    fun sortByScore(schools: List<School>, scoreOf: (String) -> Int): List<School> =
        schools.sortedByDescending { scoreOf(it.id) }
}
