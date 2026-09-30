package com.meteomontana.android.domain.usecase.admin

import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.repository.AdminRepository

/** Editar nombre/ubicación/región/estilo/roca de una escuela ya creada —
 *  desde el Historial de admin (Álvaro, 2026-09-30): "y si es una escuela?
 *  tambien quiero poder... el donde esta o el nombre". Antes solo se podía
 *  mover el pin (moveSchool); esto cubre el resto de campos de texto. */
class EditSchoolUseCase(private val repository: AdminRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(
        schoolId: String, name: String? = null, location: String? = null,
        region: String? = null, style: String? = null, rockType: String? = null
    ): School = repository.editSchool(schoolId, name, location, region, style, rockType)
}
