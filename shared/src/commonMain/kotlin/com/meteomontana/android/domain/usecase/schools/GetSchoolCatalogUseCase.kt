package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.domain.model.SchoolCatalog
import com.meteomontana.android.domain.repository.SchoolRepository

/**
 * Catálogo completo con ETag condicional: si el backend responde 304,
 * schools viene null y la pantalla sigue con su caché local.
 */
class GetSchoolCatalogUseCase(private val repository: SchoolRepository) {
    suspend operator fun invoke(etag: String? = null): SchoolCatalog =
        repository.getCatalog(etag)
}
