package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.domain.model.LineSearchHit
import com.meteomontana.android.domain.repository.SchoolRepository

/** Buscador GLOBAL de vías/bloques del catálogo (autocompletado de la lista). */
class SearchLinesUseCase(private val repo: SchoolRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(query: String): List<LineSearchHit> = repo.searchLines(query)
}
