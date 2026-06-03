package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.repository.SchoolRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SchoolRepositoryImpl @Inject constructor(
    private val api: SchoolApi
) : SchoolRepository {

    override suspend fun getSchools(
        region: String?,
        style: String?,
        rockType: List<String>?,
        lat: Double?,
        lon: Double?,
        radioKm: Double?
    ): List<School> =
        api.getSchools(
            region = region, style = style, rockType = rockType,
            lat = lat, lon = lon, radioKm = radioKm
        ).map { it.toDomain() }

    override suspend fun getSchoolById(id: String): School =
        api.getSchoolById(id).toDomain()
}
