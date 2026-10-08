package com.meteomontana.android.domain.model

/**
 * Parámetros del modo "explorar" del buscador global de vías
 * (BLOCK_SEARCH_DESIGN.md §3/§8) — espejo cliente del `ExploreRequest` del
 * backend. Todo opcional salvo `offset`; `gradeMin`/`gradeMax` van como
 * texto ("7A", "7B+"), igual que los introduce el usuario — el backend los
 * convierte a `grade_score` con la misma fórmula que ya comparten Android/iOS.
 */
data class LineExploreCriteria(
    val gradeMin: String? = null,
    val gradeMax: String? = null,
    val discipline: String? = null,       // BOULDER | ROUTE
    val rockTypes: List<String>? = null,
    val schoolIds: List<String>? = null,
    val orientations: List<String>? = null,  // N/NE/E/SE/S/SO/O/NO
    val lat: Double? = null,
    val lon: Double? = null,
    val maxDistanceKm: Double? = null,
    val sort: String? = null,             // DISTANCE | GRADE_ASC | GRADE_DESC
    val offset: Int = 0,
    /** Pide la media de estrellas y el nº de votos de cada vía (una consulta más en el servidor). */
    val withRatings: Boolean = false
) {
    /** §8.1: la pantalla entra en modo vías en cuanto hay grado puesto — es la
     *  única condición que importa para el modo implícito (§4.1); el resto de
     *  filtros son refinamientos sobre ese modo, no lo activan por sí solos. */
    val isActive: Boolean get() = gradeMin != null || gradeMax != null
}
