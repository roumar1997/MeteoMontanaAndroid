package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.gym.BoardKind
import com.meteomontana.android.domain.model.gym.BoardProblemSuggestion

/** Catalogo compartido de bloques de board con nombre (el de todas las personas; nunca dice quien). */
interface BoardProblemCatalogRepository {
    /** Hasta 10 problemas de [board] cuyo nombre contiene [query] (minimo 2 letras), con grado e inclinacion habituales. */
    @Throws(Exception::class)
    suspend fun search(board: BoardKind, query: String): List<BoardProblemSuggestion>
}
