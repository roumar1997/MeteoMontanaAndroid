package com.meteomontana.android.gym

import com.meteomontana.android.data.api.KtorGymSessionApi
import com.meteomontana.android.data.repository.LocalFirstGymSessionRepository
import com.meteomontana.android.domain.port.KeyValueStore
import com.meteomontana.android.domain.usecase.gym.GetGymSummaryUseCase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** El resumen de rocódromo del perfil se guarda al cargarlo y, sin red, se enseña esa copia (no ceros). */
class GymSummaryOfflineTest {
    private class MemStore : KeyValueStore {
        val map = HashMap<String, String>()
        override fun getString(key: String) = map[key]
        override fun putString(key: String, value: String) { map[key] = value }
    }

    private var online = true
    private val body = """{"sessions":12,"gyms":2,"ascents":40,"maxGrade":"7a","items":[{"key":"board:kilter:6c:40","count":18}]}"""

    private fun repo(store: KeyValueStore) = LocalFirstGymSessionRepository(
        KtorGymSessionApi(HttpClient(MockEngine {
            if (!online) error("sin red")
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }),
        store, { LocalDate(2026, 11, 28) }
    )

    @Test
    fun `sin red el resumen sale de la ultima copia buena`() = runTest {
        val store = MemStore()
        val uc = GetGymSummaryUseCase(repo(store))
        assertFalse(uc.hasOfflineCopy("u1"))
        assertEquals(12, uc.remote("u1")!!.sessions)
        online = false
        assertNull(uc.remote("u1"))
        assertTrue(uc.hasOfflineCopy("u1"))
        val s = uc.offline("u1")
        assertEquals(12, s.sessions)
        assertEquals(2, s.gyms)
        assertEquals(40, s.ascents)
        assertNotNull(s.items.firstOrNull { it.key == "board:kilter:6c:40" })
    }

    @Test
    fun `sin copia ni sesiones no hay datos y se sabe que no se sabe`() = runTest {
        online = false
        val uc = GetGymSummaryUseCase(repo(MemStore()))
        assertFalse(uc.hasOfflineCopy("u1"))
        assertEquals(0, uc.offline("u1").sessions)
    }
}
