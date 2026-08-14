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
            .filter { styleApiValue == null || matchesStyle(it.style, styleApiValue) }
            .filter { rockTypes.isEmpty() || rockTypes.any { r -> r.equals(it.rockType, ignoreCase = true) } }
            .filter { maxDistanceKm == null || Geo.haversineKm(userLat, userLon, it.lat, it.lon) <= maxDistanceKm }
        if (onlyFavorites) list = list.filter { it.id in favoriteIds }
        return list
    }

    /** Una escuela con estilo combinado ("Bloque,Vía") debe salir al filtrar
     *  por Vía Y al filtrar por Bloque — mismo criterio que hasStyle() en el
     *  backend (GetSchoolsUseCase.java) y matchesStyle en SchoolListView.swift
     *  (comparación exacta era el bug: "poniendo Vía o Bloque no aparecía La
     *  Pedriza, solo con Todas" — reportado 2026-08-14). */
    private fun matchesStyle(schoolStyle: String?, wanted: String): Boolean {
        if (schoolStyle == null) return false
        return schoolStyle.split(",").any { it.trim().equals(wanted, ignoreCase = true) }
    }

    /** Orden por distancia ascendente al usuario. */
    fun sortByDistance(schools: List<School>, userLat: Double, userLon: Double): List<School> =
        schools.sortedBy { Geo.haversineKm(userLat, userLon, it.lat, it.lon) }

    /** Orden por score descendente; [scoreOf] resuelve el score de cada id (hoy o
     *  tramo — lo decide el caller). Sin score → al final (convención: -1). */
    fun sortByScore(schools: List<School>, scoreOf: (String) -> Int): List<School> =
        schools.sortedByDescending { scoreOf(it.id) }
}
