package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.repository.SchoolRepository

class GetSchoolByIdUseCase(private val repository: SchoolRepository) {
    suspend operator fun invoke(id: String): School = repository.getSchoolById(id)
}
