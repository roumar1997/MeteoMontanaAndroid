package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.repository.SchoolRepository

class SearchSchoolsUseCase(private val repository: SchoolRepository) {
    suspend operator fun invoke(query: String, limit: Int = 10): List<School> =
        repository.searchSchools(query, limit)
}
