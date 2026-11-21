package com.meteomontana.android.gym

import com.meteomontana.android.data.api.KtorGymSessionApi
import com.meteomontana.android.data.repository.LocalFirstGymSessionRepository
import com.meteomontana.android.domain.model.gym.AscentFilter
import com.meteomontana.android.domain.model.gym.BoardKind
import com.meteomontana.android.domain.model.gym.BoardProblem
import com.meteomontana.android.domain.model.gym.GymAscent
import com.meteomontana.android.domain.model.gym.GymBreakdown
import com.meteomontana.android.domain.model.gym.GymSession
import com.meteomontana.android.domain.model.gym.GymStatsCalculator
import com.meteomontana.android.domain.port.KeyValueStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** «Solo board»: boards sin rocodromo (gymId 0). Cuentan en Boards y totales, no en rocodromos ni rachas; sin red se suben despues. */
class SoloBoardTest {
    private val today = LocalDate(2026, 11, 21)
    private val moon = GymAscent("board:moonboard:7a:40", 1)

    private class MemStore : KeyValueStore {
        val map = HashMap<String, String>()
        override fun getString(key: String) = map[key]
        override fun putString(key: String, value: String) { map[key] = value }
    }

    private class Server {
        var online = false
        val calls = ArrayList<String>()
        var body: String? = null
        fun client() = HttpClient(MockEngine { req ->
            if (!online) error("sin red")
            calls += "${req.method.value} ${req.url.encodedPath}"
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            when {
                req.method == HttpMethod.Put -> {
                    body = req.body.let { b -> (b as? io.ktor.http.content.TextContent)?.text ?: (b as io.ktor.http.content.ByteArrayContent).bytes().decodeToString() }
                    respond("""{"date":"2026-11-21","ascents":[{"key":"board:moonboard:7a:40","count":1}]}""", HttpStatusCode.OK, json)
                }
                req.url.encodedPath.endsWith("/me/board-sessions") -> respond("""[{"date":"2026-11-10","ascents":[{"key":"board:kilter:6c:45","count":2}],"boardProblems":[]}]""", HttpStatusCode.OK, json)
                else -> respond("[]", HttpStatusCode.OK, json)
            }
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(io.ktor.client.plugins.DefaultRequest) { headers.append(HttpHeaders.ContentType, "application/json") }
        }
    }

    private fun repo(store: KeyValueStore, server: Server) = LocalFirstGymSessionRepository(KtorGymSessionApi(server.client()), store, { today })

    @Test
    fun `sin red queda en el movil y al volver la conexion se sube a me board-sessions`() = runTest {
        val store = MemStore()
        val server = Server()
        repo(store, server).saveAscents(GymSession.NO_GYM, today, listOf(moon), listOf(BoardProblem(BoardKind.MOONBOARD, "7a", "Casa")))
        assertEquals(1, repo(store, server).sessions().size)
        assertTrue(server.calls.isEmpty())
        server.online = true
        repo(store, server).syncPending()
        assertEquals(listOf("PUT /me/board-sessions/2026-11-21"), server.calls)
        assertTrue(server.body!!.contains("Casa"))
        val n = server.calls.size
        repo(store, server).syncPending()
        assertEquals(n, server.calls.size) // ya no queda pendiente
    }

    @Test
    fun `la hoja de Solo board encuentra lo ya apuntado ese dia para no pisarlo`() = runTest {
        val store = MemStore()
        val r = repo(store, Server())
        r.saveAscents(GymSession.NO_GYM, today, listOf(moon), null)
        val found = com.meteomontana.android.domain.usecase.gym.GymTrainingUseCase(repo(store, Server())).session(GymSession.NO_GYM, today)
        assertEquals(listOf(moon), found?.ascents)
    }

    @Test
    fun `refresh trae los dias solo board del servidor`() = runTest {
        val server = Server().apply { online = true }
        val all = repo(MemStore(), server).refresh()
        assertEquals(listOf(GymSession.NO_GYM), all.map { it.gymId })
        assertEquals(2, all.single().totalAscents)
    }

    @Test
    fun `cuenta en Boards y totales pero no en rocodromos ni rachas`() {
        val gym = GymSession(5, today, ascents = listOf(GymAscent("verde", 3)))
        val solo = GymSession(GymSession.NO_GYM, LocalDate(2026, 11, 20), ascents = listOf(moon, GymAscent("board:moonboard:6a:40", 2)),
            boardProblems = listOf(BoardProblem(BoardKind.MOONBOARD, "7a", "Casa")))
        val s = GymBreakdown.summarize(listOf(gym, solo), today)
        assertEquals(1, s.sessions)
        assertEquals(1, s.gyms)
        assertEquals(listOf(5L), s.byGym.map { it.gymId })
        assertEquals(6, s.ascents)
        assertEquals("7a", s.maxGrade)
        assertEquals(3, GymBreakdown.boardCount(GymBreakdown.filter(s.items, AscentFilter.BOARDS)))
        assertEquals(listOf(0L), s.boardProblems.map { it.gymId })
        assertEquals(1, GymStatsCalculator.compute(listOf(gym, solo), today).year)
    }
}
