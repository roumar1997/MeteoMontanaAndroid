package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.domain.repository.SchoolRepository

/** "Las he visto": marca la escuela como zona conocida de procesionaria (para siempre). */
class ConfirmProcessionaryUseCase(private val repository: SchoolRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(schoolId: String) = repository.confirmProcessionary(schoolId)
}
