package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.model.SchoolCatalog

interface SchoolRepository {
    /**
     * Catálogo completo con soporte ETag/304: si etag != null se manda como
     * If-None-Match y el backend responde 304 cuando nada cambió — el cliente
     * reusa entonces su caché local sin re-descargar las ~191 escuelas.
     */
    suspend fun getCatalog(etag: String? = null): SchoolCatalog

    suspend fun getSchools(
        region: String? = null,
        style: String? = null,
        rockType: List<String>? = null,
        lat: Double? = null,
        lon: Double? = null,
        radioKm: Double? = null
    ): List<School>

    suspend fun getSchoolById(id: String): School

    suspend fun searchSchools(query: String, limit: Int = 10): List<School>
}
