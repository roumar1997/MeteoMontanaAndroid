package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorSchoolApi
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.repository.SchoolRepository

class KtorSchoolRepository(private val api: KtorSchoolApi) : SchoolRepository {

    override suspend fun getSchools(
        region: String?, style: String?, rockType: List<String>?,
        lat: Double?, lon: Double?, radioKm: Double?
    ): List<School> = api.getSchools(region, style, rockType, lat, lon, radioKm).map { it.toDomain() }

    override suspend fun getSchoolById(id: String): School = api.getSchoolById(id).toDomain()

    override suspend fun searchSchools(query: String, limit: Int): List<School> =
        api.searchSchools(query, limit).map { it.toDomain() }
}
