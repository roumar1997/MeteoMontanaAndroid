package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.repository.SchoolRepository
import javax.inject.Inject

/**
 * Carga la lista de escuelas filtrada por región, estilo, roca y radio
 * desde una posición. Delega en `SchoolRepository`; el use case existe
 * para que el ViewModel no dependa directamente del repositorio y para
 * poder mover esta lógica a `commonMain` en Fase 2 sin tocar la UI.
 */
class GetSchoolsUseCase @Inject constructor(
    private val repository: SchoolRepository
) {
    suspend operator fun invoke(
        region: String? = null,
        style: String? = null,
        rockType: List<String>? = null,
        lat: Double? = null,
        lon: Double? = null,
        radioKm: Double? = null
    ): List<School> = repository.getSchools(region, style, rockType, lat, lon, radioKm)
}
