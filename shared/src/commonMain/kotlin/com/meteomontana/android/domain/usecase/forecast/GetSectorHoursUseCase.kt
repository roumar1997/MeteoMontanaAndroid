package com.meteomontana.android.domain.usecase.forecast

import com.meteomontana.android.domain.model.SectorHours
import com.meteomontana.android.domain.repository.SectorHoursRepository

/** Mejor hora por sector de hoy (fila plegable de la tarjeta de la nota). Se pide al desplegar; sin red, falla. */
class GetSectorHoursUseCase(private val repository: SectorHoursRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(schoolId: String): List<SectorHours> = repository.get(schoolId, null)
}
