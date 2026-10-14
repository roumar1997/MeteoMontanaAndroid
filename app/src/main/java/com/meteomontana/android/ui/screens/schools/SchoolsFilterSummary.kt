package com.meteomontana.android.ui.screens.schools

/**
 * Lo que la lista de Escuelas dice de sus filtros SIN desplegarlos (2026-10-14: los filtros van plegados por defecto,
 * "demasiadas cosas a la vista"). Funciones puras para poder probarlas: la pantalla solo las pinta.
 */

/** Cuántos filtros se han movido respecto a lo de siempre (50 km, todo, todo). */
fun activeFilterCount(
    filters: SchoolFilters,
    tab: ExploreTab,
    gradeMin: String?,
    gradeMax: String?,
    orientations: Set<String>,
    exploreSort: ExploreSortBy,
    defaultExploreSort: ExploreSortBy = ExploreSortBy.Distance
): Int {
    var n = 0
    if (filters.maxDistanceKm != SchoolFilters().maxDistanceKm) n += 1
    if (filters.style != StyleFilter.All) n += 1
    if (filters.rockTypes.isNotEmpty()) n += 1
    if (tab == ExploreTab.Schools) {
        if (filters.onlyFavorites || filters.onlySavedOffline) n += 1
    } else {
        if (gradeMin != null || gradeMax != null) n += 1
        if (orientations.isNotEmpty()) n += 1
        if (exploreSort != defaultExploreSort) n += 1
    }
    return n
}

/**
 * Etiqueta del chip de días: "HOY" sin días; "VIE" con uno; "VIE–DOM" si son seguidos; "3 DÍAS" si no.
 *
 * Los días elegidos son del día de la semana ISO (1 = lunes … 7 = domingo) dentro de la ventana de los próximos 7
 * días a partir de hoy ([todayIso]); "seguidos" se mide en esa ventana (jueves a lunes lo son aunque crucen domingo).
 */
fun dayRangeLabel(
    selected: Set<Int>,
    todayIso: Int,
    dayLetters: List<String>,
    todayLabel: String,
    nDaysLabel: (Int) -> String
): String {
    if (selected.isEmpty()) return todayLabel
    val offsets = selected.map { ((it - todayIso) % 7 + 7) % 7 }.sorted()
    fun name(offset: Int) = dayLetters[(todayIso - 1 + offset) % 7]
    if (offsets.size == 1) return name(offsets.first())
    val consecutive = offsets.last() - offsets.first() == offsets.size - 1
    return if (consecutive) "${name(offsets.first())}–${name(offsets.last())}" else nDaysLabel(offsets.size)
}
