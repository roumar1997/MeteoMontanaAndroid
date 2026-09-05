package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.domain.repository.SchoolRepository

/** "Hay ahora mismo, antes de tiempo": activa la alarma ya, con caducidad. */
class ReportProcessionaryActiveNowUseCase(private val repository: SchoolRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(schoolId: String) = repository.reportProcessionaryActiveNow(schoolId)
}
