package com.meteomontana.android.domain.model

/**
 * Resultado de pedir el catálogo completo con ETag condicional.
 * schools == null significa 304 Not Modified: la caché local sigue vigente.
 */
data class SchoolCatalog(
    val schools: List<School>?,
    val etag: String?
)
