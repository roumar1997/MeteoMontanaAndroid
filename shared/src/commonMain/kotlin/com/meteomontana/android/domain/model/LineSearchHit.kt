package com.meteomontana.android.domain.model

/** Resultado del buscador GLOBAL de vías/bloques (pantalla de Escuelas). */
data class LineSearchHit(
    val schoolId: String,
    val schoolName: String,
    val blockId: String,
    val blockName: String,
    val lineId: String?,
    val lineName: String?,
    val grade: String?,
    val sectorName: String?,
    /** Foto de la cara de la vía (o portada de la piedra) — para el mini-topo. */
    val photoPath: String?,
    /** Trazo normalizado de la vía (null en piedras o backends viejos). */
    val linePath: String?,
    val startType: String?,
    /** Coordenadas de la PIEDRA (no de la escuela) — BLOCK_SEARCH_DESIGN.md
     *  §1.4. Solo el modo "explorar" las rellena; null en la búsqueda de texto
     *  de siempre. */
    val lat: Double? = null,
    val lon: Double? = null,
    /** Aspecto votado por la comunidad (N/NE/.../NO), o null si nadie lo ha
     *  votado todavía — BLOCK_SEARCH_DESIGN.md §8.1. Solo modo "explorar". */
    val orientation: String? = null,
    /** Modalidad de la piedra: "BOULDER" (bloque) o "ROUTE" (vía). null con
     *  backends viejos → los iconos caen al estilo de la escuela. */
    val discipline: String? = null,
    /** Media de estrellas (1-5), o null si nadie la ha valorado o no se pidieron. */
    val rating: Double? = null,
    /** Cuántas personas la han valorado (null si no se pidieron). */
    val ratingCount: Int? = null
)
