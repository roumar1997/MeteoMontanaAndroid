package com.meteomontana.android.data

import com.meteomontana.android.data.api.KtorFeedApi
import com.meteomontana.android.data.repository.KtorFeedRepository
import com.meteomontana.android.domain.port.KeyValueStore
import com.meteomontana.android.domain.usecase.feed.GetFeedPostCountUseCase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** «Mis publicaciones»: con red la cuenta del servidor; sin red la última que se vio; si nunca se vio, falla (la UI pone «—»). */
class PostCountOfflineTest {
    private class MemStore : KeyValueStore {
        val map = HashMap<String, String>()
        override fun getString(key: String) = map[key]
        override fun putString(key: String, value: String) { map[key] = value }
    }

    private var online = true

    private fun useCase(store: KeyValueStore) = GetFeedPostCountUseCase(
        KtorFeedRepository(KtorFeedApi(HttpClient(MockEngine {
            if (!online) error("sin red")
            respond("""{"count":4}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } })),
        store
    )

    @Test
    fun `sin red devuelve la ultima cuenta vista`() = runTest {
        val uc = useCase(MemStore())
        assertEquals(4, uc())
        online = false
        assertEquals(4, uc())
    }

    @Test
    fun `sin red y sin copia lanza en vez de inventar un cero`() = runTest {
        online = false
        val uc = useCase(MemStore())
        assertFailsWith<Exception> { uc() }
    }
}
