package com.meteomontana.android.domain.usecase.restriction

import com.meteomontana.android.domain.model.Restriction
import com.meteomontana.android.domain.model.SchoolRestrictions
import com.meteomontana.android.domain.repository.RestrictionDraft
import com.meteomontana.android.domain.repository.RestrictionRepository

/** Las restricciones en vigor de una escuela (para pintar "!" en sus sectores y bloques). */
class GetSchoolActiveRestrictionsUseCase(private val repository: RestrictionRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(schoolId: String, forceRefresh: Boolean = false): SchoolRestrictions =
        SchoolRestrictions.of(schoolId, repository.getActive(forceRefresh))
}

/** Todas las en vigor (el buscador y el asistente marcan resultados de cualquier escuela). */
class GetActiveRestrictionsUseCase(private val repository: RestrictionRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(forceRefresh: Boolean = false): List<Restriction> = repository.getActive(forceRefresh)
}

/** Admin: todas las de una escuela, en vigor o no. */
class GetSchoolRestrictionsUseCase(private val repository: RestrictionRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(schoolId: String): List<Restriction> = repository.getBySchool(schoolId)
}

/** Admin: crear (id null) o editar. */
class SaveRestrictionUseCase(private val repository: RestrictionRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(id: String?, draft: RestrictionDraft): Restriction = repository.adminSave(id, draft)
}

class DeleteRestrictionUseCase(private val repository: RestrictionRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(id: String) = repository.adminDelete(id)
}
