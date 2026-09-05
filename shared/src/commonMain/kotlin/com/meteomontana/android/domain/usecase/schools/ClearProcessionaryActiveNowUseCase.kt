package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.domain.repository.SchoolRepository

/** Deshace solo el aviso puntual "antes de tiempo" (no toca "Las he visto"). */
class ClearProcessionaryActiveNowUseCase(private val repository: SchoolRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(schoolId: String) = repository.clearProcessionaryActiveNow(schoolId)
}
