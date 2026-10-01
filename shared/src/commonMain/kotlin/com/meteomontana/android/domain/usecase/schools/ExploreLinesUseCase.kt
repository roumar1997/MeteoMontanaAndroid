package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.domain.model.LineExploreCriteria
import com.meteomontana.android.domain.model.LineSearchHit
import com.meteomontana.android.domain.repository.SchoolRepository

/** Modo "explorar" del buscador global de vías — filtros sin texto libre
 *  (BLOCK_SEARCH_DESIGN.md §3/§8). */
class ExploreLinesUseCase(private val repo: SchoolRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(criteria: LineExploreCriteria): List<LineSearchHit> = repo.exploreLines(criteria)
}
