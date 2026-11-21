package com.meteomontana.android.domain.usecase.gym

import com.meteomontana.android.domain.model.gym.BoardKind
import com.meteomontana.android.domain.model.gym.BoardProblemRepeats
import com.meteomontana.android.domain.model.gym.BoardProblemSuggestion
import com.meteomontana.android.domain.repository.BoardProblemCatalogRepository
import com.meteomontana.android.domain.repository.GymSessionRepository
import kotlin.coroutines.cancellation.CancellationException

/**
 * Autocompletar del nombre de un bloque de board mientras se escribe (minimo 2 letras): primero lo que la persona ya hizo
 * (del movil) y despues el catalogo de todas las personas. Sin red solo sale lo propio.
 */
class SearchBoardProblemsUseCase(private val catalog: BoardProblemCatalogRepository, private val sessions: GymSessionRepository) {

    /** Solo lo propio, al instante y sin red. */
    fun local(board: BoardKind, query: String): List<BoardProblemSuggestion> =
        BoardProblemRepeats.suggestLocal(BoardProblemRepeats.history(sessions.sessions()), board, query)

    /** Lo propio + el catalogo; si el servidor falla o no hay red, solo lo propio. */
    @Throws(Exception::class)
    suspend fun search(board: BoardKind, query: String): List<BoardProblemSuggestion> {
        val own = local(board, query)
        if (BoardProblemRepeats.nameKey(query).length < BoardProblemRepeats.SUGGEST_MIN_CHARS) return own
        val remote = try { catalog.search(board, query) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
        return BoardProblemRepeats.merge(own, remote)
    }
}
