package com.meteomontana.android.gym

import com.meteomontana.android.data.api.KtorGymSessionApi
import com.meteomontana.android.data.repository.LocalFirstGymSessionRepository
import com.meteomontana.android.domain.model.gym.AscentEntries
import com.meteomontana.android.domain.model.gym.AscentFilter
import com.meteomontana.android.domain.model.gym.BoardKind
import com.meteomontana.android.domain.model.gym.BoardProblem
import com.meteomontana.android.domain.model.gym.GymAscent
import com.meteomontana.android.domain.model.gym.GymBreakdown
import com.meteomontana.android.domain.model.gym.GymSession
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

/** Entrenos sin red: se apuntan en el movil, quedan pendientes y se suben (con los nombres de los boards) al volver la conexion. */
class GymOfflineQueueTest {
    private val today = LocalDate(2026, 11, 21)

    private class MemStore : KeyValueStore {
        val map = HashMap<String, String>()
        override fun getString(key: String) = map[key]
        override fun putString(key: String, value: String) { map[key] = value }
    }

    private class Server {
        var online = false
        var serverSession: String? = null
        val calls = ArrayList<String>()
        var putBody: String? = null
        var pastBody: String? = null

        fun client() = HttpClient(MockEngine { req ->
            if (!online) error("sin red")
            calls += "${req.method.value} ${req.url.encodedPath}"
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            when {
                req.method == HttpMethod.Post && req.url.encodedPath.endsWith("/sessions/today") -> {
                    serverSession = """{"id":55,"gymId":7,"date":"2026-11-21"}"""
                    respond(serverSession!!, HttpStatusCode.OK, json)
                }
                req.method == HttpMethod.Post && req.url.encodedPath.endsWith("/gyms/7/sessions") -> {
                    pastBody = (req.body as io.ktor.http.content.TextContent).text
                    respond("""{"id":56,"gymId":7,"date":"2026-11-20"}""", HttpStatusCode.OK, json)
                }
                req.method == HttpMethod.Put -> {
                    putBody = req.body.let { b -> (b as? io.ktor.http.content.TextContent)?.text ?: (b as io.ktor.http.content.ByteArrayContent).bytes().decodeToString() }
                    respond("""{"id":55,"gymId":7,"date":"2026-11-21","ascents":[{"key":"board:kilter:7b:40","count":1}],"boardProblems":[{"board":"kilter","grade":"7b","name":"Fanjulandia"}]}""", HttpStatusCode.OK, json)
                }
                req.method == HttpMethod.Get -> respond("[${serverSession ?: ""}]".replace("[]", "[]"), HttpStatusCode.OK, json)
                else -> respond("", HttpStatusCode.NoContent)
            }
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(io.ktor.client.plugins.DefaultRequest) { headers.append(HttpHeaders.ContentType, "application/json") }
        }
    }

    private fun repo(store: KeyValueStore, server: Server) = LocalFirstGymSessionRepository(KtorGymSessionApi(server.client()), store, { today })

    private val kilter = GymAscent("board:kilter:7b:40", 1)
    private val named = listOf(BoardProblem(BoardKind.KILTER, "7b", "Fanjulandia"))

    @Test
    fun `sin red se ve en el movil y al volver la conexion se sube con su nombre`() = runTest {
        val store = MemStore()
        val server = Server()
        val r = repo(store, server)
        r.checkInToday(7, com.meteomontana.android.domain.model.gym.GymSessionSource.MANUAL)
        r.saveAscents(7, today, listOf(kilter), named)
        // Offline: lo apuntado esta en el movil, con su nombre.
        val local = repo(store, server).sessions().single()
        assertEquals(listOf("Fanjulandia"), local.boardProblems.map { it.name })
        assertEquals(0L, local.id)
        assertTrue(server.calls.isEmpty())
        // Vuelve la red: syncPending da de alta el dia y sube ascensiones y nombres.
        server.online = true
        repo(store, server).syncPending()
        assertTrue(server.calls.contains("POST /gyms/7/sessions/today"))
        assertTrue(server.calls.any { it.startsWith("PUT") }, server.calls.toString())
        assertTrue(server.putBody!!.contains("Fanjulandia"))
        assertEquals(55L, repo(store, server).sessions().single().id)
        // Ya no queda nada pendiente: otra sincronizacion no vuelve a llamar.
        val n = server.calls.size
        repo(store, server).syncPending()
        assertEquals(n, server.calls.size)
    }

    @Test
    fun `un entreno de ayer apuntado sin red se da de alta con su fecha al volver la conexion`() = runTest {
        val store = MemStore()
        val server = Server()
        val yesterday = LocalDate(2026, 11, 20)
        repo(store, server).saveAscents(7, yesterday, listOf(GymAscent("verde", 3)))
        assertEquals(0L, repo(store, server).sessions().single().id)
        assertTrue(server.calls.isEmpty())
        server.online = true
        repo(store, server).syncPending()
        assertEquals(listOf("POST /gyms/7/sessions", "PUT /gyms/sessions/56/ascents"), server.calls)
        assertTrue(server.pastBody!!.contains("2026-11-20"), server.pastBody)
        assertEquals(56L, repo(store, server).sessions().single().id)
        val n = server.calls.size
        repo(store, server).syncPending()
        assertEquals(n, server.calls.size)
    }

    @Test
    fun `refresh sube lo pendiente antes de traer lo del servidor y lo pendiente manda`() = runTest {
        val store = MemStore()
        val server = Server()
        val r = repo(store, server)
        r.saveAscents(7, today, listOf(kilter), named)
        server.online = true
        val merged = repo(store, server).refresh()
        assertEquals(listOf("Fanjulandia"), merged.single().boardProblems.map { it.name })
        assertEquals(1, merged.single().totalAscents)
    }

    @Test
    fun `un entreno borrado sin red se borra del servidor al volver`() = runTest {
        val store = MemStore()
        val server = Server()
        server.online = true
        val r = repo(store, server)
        r.saveAscents(7, today, listOf(kilter), named) // ya subido: id 55
        server.online = false
        r.saveAscents(7, today, emptyList())
        assertTrue(r.sessions().isEmpty())
        server.calls.clear()
        server.online = true
        repo(store, server).syncPending()
        assertEquals(listOf("DELETE /gyms/sessions/55"), server.calls)
    }

    @Test
    fun `los boards no se reparten por rocodromo en Todo`() {
        val sessions = listOf(
            GymSession(1, today, ascents = listOf(GymAscent("verde", 2), GymAscent("board:kilter:7b:40", 1))),
            GymSession(2, today, ascents = listOf(GymAscent("board:kilter:6c:40", 3)))
        )
        val sum = GymBreakdown.summarize(sessions, today)
        val all = GymBreakdown.gymsFilteredWithoutBoards(sum, AscentFilter.ALL)
        assertEquals(listOf(1L), all.map { it.gymId })
        assertEquals(2, all.single().ascents)
        assertEquals(4, GymBreakdown.boardCount(sum.items))
        // Boards sigue juntando todos los rocodromos.
        assertEquals(2, GymBreakdown.gymsFilteredWithoutBoards(sum, AscentFilter.BOARDS).size)
        assertEquals(listOf("verde"), GymBreakdown.visitWithoutBoards(sum.byGym.first { it.gymId == 1L }).items.map { it.key })
        assertTrue(AscentEntries.entries(sum, AscentFilter.ALL, setOf("7b"), includeBoards = false).isEmpty())
        assertEquals(1, AscentEntries.entries(sum, AscentFilter.ALL, setOf("7b")).sumOf { it.count })
    }
}
