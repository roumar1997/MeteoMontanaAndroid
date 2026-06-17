package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.repository.SchoolRepository

class GetSchoolsUseCase(private val repository: SchoolRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(
        region: String? = null,
        style: String? = null,
        rockType: List<String>? = null,
        lat: Double? = null,
        lon: Double? = null,
        radioKm: Double? = null
    ): List<School> = repository.getSchools(region, style, rockType, lat, lon, radioKm)
}
