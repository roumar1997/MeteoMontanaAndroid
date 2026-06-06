package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.School

interface SchoolRepository {
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
