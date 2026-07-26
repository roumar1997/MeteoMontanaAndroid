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
    val sectorName: String?
)
