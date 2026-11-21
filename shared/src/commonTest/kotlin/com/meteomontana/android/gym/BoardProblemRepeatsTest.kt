package com.meteomontana.android.gym

import com.meteomontana.android.data.api.KtorGymSessionApi
import com.meteomontana.android.data.repository.LocalFirstGymSessionRepository
import com.meteomontana.android.domain.model.gym.BoardKind
import com.meteomontana.android.domain.model.gym.BoardProblem
import com.meteomontana.android.domain.model.gym.BoardProblemRepeats
import com.meteomontana.android.domain.model.gym.BoardProblemSuggestion
import com.meteomontana.android.domain.model.gym.BoardProblems
import com.meteomontana.android.domain.model.gym.GymAscent
import com.meteomontana.android.domain.model.gym.GymSession
import com.meteomontana.android.domain.model.gym.NamedBoardProblem
import com.meteomontana.android.domain.port.KeyValueStore
import com.meteomontana.android.domain.repository.BoardProblemCatalogRepository
import com.meteomontana.android.domain.usecase.gym.SearchBoardProblemsUseCase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Repetir el mismo bloque con nombre (entrenos de vueltas) y autocompletar nombres. */
class BoardProblemRepeatsTest {
    private fun named(date: String, name: String, grade: String = "7b", board: BoardKind = BoardKind.KILTER, gym: Long = 0) =
        NamedBoardProblem(gym, date, BoardProblem(board, grade, name))

    @Test fun repeatedAddsOneNamePerRepAndKeepsRecountInSync() {
        val problems = BoardProblemRepeats.addRepeated(emptyList(), BoardKind.KILTER, "7B", "  Fanjulandia ", 3)
        assertEquals(3, problems.size)
        assertTrue(problems.all { it == BoardProblem(BoardKind.KILTER, "7b", "Fanjulandia") })
        // los recuentos mandan: 3 ascensiones, 3 nombres; con 2 ascensiones solo caben 2
        val asc = listOf(GymAscent("board:kilter:7b:40", 3))
        assertEquals(3, BoardProblems.prune(problems, asc).size)
        assertEquals(2, BoardProblems.prune(problems, listOf(GymAscent("board:kilter:7b:40", 2))).size)
    }

    @Test fun addRepeatedIgnoresEmptyNamesClampsRepsAndSessionCap() {
        assertEquals(emptyList(), BoardProblemRepeats.addRepeated(emptyList(), BoardKind.KILTER, "7b", "  ", 5))
        assertEquals(20, BoardProblemRepeats.addRepeated(emptyList(), BoardKind.KILTER, "7b", "A", 99).size)
        assertEquals(1, BoardProblemRepeats.addRepeated(emptyList(), BoardKind.KILTER, "7b", "A", 0).size)
        val full = List(99) { BoardProblem(BoardKind.KILTER, "6a", "B") }
        assertEquals(100, BoardProblemRepeats.addRepeated(full, BoardKind.KILTER, "7b", "A", 5).size)
    }

    @Test fun groupsSameNameIgnoringCaseAccentsAndSpacesPerBoardAndGrade() {
        val all = listOf(
            named("2026-11-20", "Fanjulandia"), named("2026-11-20", "fanjulandia"), named("2026-11-18", " FANJULÁNDIA ", gym = 5),
            named("2026-11-19", "Fanjulandia", grade = "7a"),               // mismo nombre, otro grado: aparte
            named("2026-11-19", "Fanjulandia", board = BoardKind.TENSION),  // otro board: no sale en Kilter
            named("2026-11-01", "Otro", grade = "6a")
        )
        val out = BoardProblemRepeats.repeated(all, BoardKind.KILTER, "")
        assertEquals(listOf("Fanjulandia" to "7b", "Fanjulandia" to "7a", "Otro" to "6a"), out.map { it.name to it.grade })
        val f = out.first()
        assertEquals(3, f.reps)
        assertEquals("2026-11-20", f.lastDate)
        assertEquals("2026-11-18", f.firstDate)
        assertEquals(listOf("2026-11-20", "2026-11-18"), f.dates)
        assertEquals(1, out[1].reps)
    }

    @Test fun repeatedRespectsSearchAndAngleFilter() {
        val all = listOf(named("2026-11-20", "Fanjulandia"), named("2026-11-20", "Otro", grade = "6a"))
        assertEquals(listOf("Fanjulandia"), BoardProblemRepeats.repeated(all, BoardKind.KILTER, "fanj").map { it.name })
        assertEquals(emptyList(), BoardProblemRepeats.repeated(all, BoardKind.KILTER, "zzz"))
        // el filtro por inclinacion se aplica antes (BoardAngles.problemsAt) y la agrupacion sigue funcionando
        val items = listOf(GymAscent("board:kilter:7b:40", 1), GymAscent("board:kilter:6a:45", 1))
        val at40 = com.meteomontana.android.domain.model.gym.BoardAngles.problemsAt(all, items, BoardKind.KILTER, 40)
        assertEquals(listOf("Fanjulandia"), BoardProblemRepeats.repeated(at40, BoardKind.KILTER, "").map { it.name })
    }

    @Test fun localSuggestionsNeedTwoLettersAndRankPrefixThenFrequency() {
        val all = listOf(
            named("2026-11-20", "Fanjulandia"), named("2026-11-19", "Fanjulandia"), named("2026-11-18", "Fanjulandia", grade = "7b"),
            named("2026-11-20", "El fantasma", grade = "6c"), named("2026-11-01", "Fanta", grade = "6a"),
            named("2026-11-02", "Fanatico", board = BoardKind.TENSION)
        )
        assertEquals(emptyList(), BoardProblemRepeats.suggestLocal(all, BoardKind.KILTER, "f"))
        val out = BoardProblemRepeats.suggestLocal(all, BoardKind.KILTER, "FAN")
        assertEquals(listOf("Fanjulandia", "Fanta", "El fantasma"), out.map { it.name })
        assertEquals(3, out.first().count)
        assertTrue(out.all { it.mine })
        assertEquals(listOf("Fanjulandia"), BoardProblemRepeats.suggestLocal(all, BoardKind.KILTER, "julan").map { it.name })
    }

    @Test fun mergeKeepsOwnFirstDedupesAndTakesSharedAngle() {
        val own = listOf(BoardProblemSuggestion("Fanjulandia", "7b", null, 2, mine = true))
        val remote = listOf(
            BoardProblemSuggestion("fanjulandia", "7b", 40, 9), BoardProblemSuggestion("Fanta", "6a", 45, 4)
        )
        val out = BoardProblemRepeats.merge(own, remote)
        assertEquals(listOf("Fanjulandia", "Fanta"), out.map { it.name })
        assertEquals(40, out.first().angle)
        assertTrue(out.first().mine)
        assertEquals(false, out[1].mine)
    }

    // --- caso de uso con sesiones del movil y catalogo falso ---

    private class MemStore : KeyValueStore {
        val map = HashMap<String, String>()
        override fun getString(key: String) = map[key]
        override fun putString(key: String, value: String) { map[key] = value }
    }

    private class FakeCatalog(var fail: Boolean = false) : BoardProblemCatalogRepository {
        var calls = 0
        override suspend fun search(board: BoardKind, query: String): List<BoardProblemSuggestion> {
            calls++
            if (fail) error("sin red")
            return listOf(BoardProblemSuggestion("Fanta compartida", "6b", 40, 12))
        }
    }

    private suspend fun useCase(catalog: BoardProblemCatalogRepository): SearchBoardProblemsUseCase {
        val client = HttpClient(MockEngine { error("sin red") })
        val repo = LocalFirstGymSessionRepository(KtorGymSessionApi(client), MemStore(), { LocalDate(2026, 11, 21) })
        repo.saveAscents(GymSession.NO_GYM, LocalDate(2026, 11, 20), listOf(GymAscent("board:kilter:7b:40", 2)),
            List(2) { BoardProblem(BoardKind.KILTER, "7b", "Fanjulandia") })
        return SearchBoardProblemsUseCase(catalog, repo)
    }

    @Test fun searchMergesOwnAndSharedAndSkipsServerBelowTwoLetters() = runTest {
        val catalog = FakeCatalog()
        val uc = useCase(catalog)
        assertEquals(listOf("Fanjulandia", "Fanta compartida"), uc.search(BoardKind.KILTER, "fan").map { it.name })
        assertEquals(1, catalog.calls)
        assertEquals(emptyList(), uc.search(BoardKind.KILTER, "f"))
        assertEquals(1, catalog.calls)
    }

    @Test fun searchFallsBackToOwnNamesWhenOffline() = runTest {
        val uc = useCase(FakeCatalog(fail = true))
        val out = uc.search(BoardKind.KILTER, "fanj")
        assertEquals(listOf("Fanjulandia"), out.map { it.name })
        assertEquals("7b", out.single().grade)
    }
}
