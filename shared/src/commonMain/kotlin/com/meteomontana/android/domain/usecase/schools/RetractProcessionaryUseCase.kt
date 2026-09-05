package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.domain.repository.SchoolRepository

/** "Me equivoqué al pulsar": deshace una confirmación de procesionaria. */
class RetractProcessionaryUseCase(private val repository: SchoolRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(schoolId: String) = repository.retractProcessionary(schoolId)
}
