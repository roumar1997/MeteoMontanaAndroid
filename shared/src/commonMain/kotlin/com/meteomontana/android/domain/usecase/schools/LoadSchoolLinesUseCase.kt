package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.domain.model.LineExploreCriteria
import com.meteomontana.android.domain.model.LineSearchHit

/** Tope de páginas por consulta (el servidor corta por páginas): muy por encima de cualquier escuela real. */
internal const val MAX_LINE_PAGES = 8

/**
 * Pide TODAS las páginas de una consulta de vías. No hace falta conocer el tamaño de página del servidor:
 * el desplazamiento de cada petición es lo ya recibido, y se para cuando llega una página vacía.
 */
internal suspend fun fetchAllLinePages(
    base: LineExploreCriteria,
    fetch: suspend (LineExploreCriteria) -> List<LineSearchHit>,
    maxPages: Int = MAX_LINE_PAGES
): List<LineSearchHit> {
    val out = mutableListOf<LineSearchHit>()
    repeat(maxPages) {
        val page = fetch(base.copy(offset = out.size))
        if (page.isEmpty()) return out
        out += page
    }
    return out
}

/**
 * Todas las vías de una escuela, ya con sus estrellas y sus repeticiones (personas distintas que las tienen
 * hechas en su diario). Una sola descarga: los filtros se aplican en local con [SchoolLinesExplorer].
 */
class LoadSchoolLinesUseCase(private val explore: ExploreLinesUseCase) {
    @Throws(Exception::class)
    suspend operator fun invoke(schoolId: String): List<LineSearchHit> =
        fetchAllLinePages(
            LineExploreCriteria(
                schoolIds = listOf(schoolId),
                sort = "MOST_REPEATED",
                withRatings = true,
                withRepetitions = true
            ),
            fetch = { explore(it) }
        )
}

/** Ids de las vías de una escuela que tienen beta de esa altura ([BetaHeights]). */
class LoadBetaLineIdsUseCase(private val explore: ExploreLinesUseCase) {
    @Throws(Exception::class)
    suspend operator fun invoke(schoolId: String, height: String): Set<String> =
        fetchAllLinePages(
            LineExploreCriteria(schoolIds = listOf(schoolId), sort = "DISTANCE", beta = height),
            fetch = { explore(it) }
        ).mapNotNull { it.lineId }.toSet()
}
