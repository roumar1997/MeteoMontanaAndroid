package com.meteomontana.android.data.repository

import com.meteomontana.android.domain.model.gym.BoardKind
import com.meteomontana.android.domain.model.gym.BoardProblemSuggestion
import com.meteomontana.android.domain.repository.BoardProblemCatalogRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

/** GET boards/problems/search?board=&q= (catalogo compartido; solo nombre, grado, inclinacion y cuanta gente). */
@Serializable
data class BoardProblemSuggestionDto(val board: String = "", val name: String = "", val grade: String = "", val angle: Int? = null, val count: Int = 0)

fun BoardProblemSuggestionDto.toDomainOrNull(): BoardProblemSuggestion? =
    if (name.isBlank() || grade.isBlank()) null else BoardProblemSuggestion(name, grade.lowercase(), angle, count)

class KtorBoardProblemCatalogRepository(private val client: HttpClient) : BoardProblemCatalogRepository {
    @Throws(Exception::class)
    override suspend fun search(board: BoardKind, query: String): List<BoardProblemSuggestion> =
        client.get("boards/problems/search") {
            parameter("board", board.wire)
            parameter("q", query.trim())
            parameter("limit", 10)
        }.body<List<BoardProblemSuggestionDto>>().mapNotNull { it.toDomainOrNull() }
}
