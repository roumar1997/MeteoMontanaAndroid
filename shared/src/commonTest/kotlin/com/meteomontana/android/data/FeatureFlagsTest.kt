package com.meteomontana.android.data

import com.meteomontana.android.data.api.KtorFeatureFlagApi
import com.meteomontana.android.data.api.buildApiHttpClient
import com.meteomontana.android.data.repository.FeatureFlagRepositoryImpl
import com.meteomontana.android.domain.model.FeatureDisabledException
import com.meteomontana.android.domain.model.FeatureFlags
import com.meteomontana.android.domain.model.FeatureKey
import com.meteomontana.android.domain.port.KeyValueStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeatureFlagsTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private val assistantOff = """
        {"flags":{"assistant":{"enabled":false,"message":"Asistente en mantenimiento"},
                  "feed_publish":{"enabled":true,"message":null}},"refreshAfterSeconds":300}
    """.trimIndent()

    private class MemoryStore : KeyValueStore {
        val map = mutableMapOf<String, String>()
        override fun getString(key: String) = map[key]
        override fun putString(key: String, value: String) { map[key] = value }
    }

    private class Harness(
        var response: () -> Pair<HttpStatusCode, String>,
        val store: MemoryStore = MemoryStore(),
        var now: Long = 1_000_000L
    ) {
        var calls = 0
        private val engine = MockEngine { _ ->
            calls++
            val (status, body) = response()
            respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        val api = KtorFeatureFlagApi(buildApiHttpClient("https://x/", engine) { null })
        fun repo() = FeatureFlagRepositoryImpl(api, store) { now }
    }

    @Test
    fun `una clave desconocida cuenta como encendida`() {
        val flags = FeatureFlags()
        assertTrue(flags.isEnabled("loquesea"))
        assertNull(flags.messageFor("loquesea"))
    }

    @Test
    fun `nada guardado y sin red deja todo encendido`() = runTest {
        val h = Harness({ HttpStatusCode.InternalServerError to "{}" })
        val repo = h.repo()
        repo.refresh()
        assertTrue(repo.flags.value.isEnabled(FeatureKey.ASSISTANT))
    }

    @Test
    fun `tras refrescar refleja el servidor y el mensaje`() = runTest {
        val h = Harness({ HttpStatusCode.OK to assistantOff })
        val repo = h.repo()
        repo.refresh()
        assertFalse(repo.flags.value.isEnabled(FeatureKey.ASSISTANT))
        assertEquals("Asistente en mantenimiento", repo.flags.value.messageFor(FeatureKey.ASSISTANT))
        assertTrue(repo.flags.value.isEnabled(FeatureKey.FEED_PUBLISH))
    }

    @Test
    fun `sin red se conserva lo ultimo guardado en un arranque nuevo`() = runTest {
        val h = Harness({ HttpStatusCode.OK to assistantOff })
        h.repo().refresh()

        h.response = { HttpStatusCode.ServiceUnavailable to "{}" }
        h.now += 60_000
        val reopened = h.repo()
        reopened.refresh()

        assertFalse(reopened.flags.value.isEnabled(FeatureKey.ASSISTANT))
    }

    @Test
    fun `si falla la red se conserva el valor que ya tenia`() = runTest {
        val h = Harness({ HttpStatusCode.OK to assistantOff })
        val repo = h.repo()
        repo.refresh()
        h.response = { HttpStatusCode.InternalServerError to "{}" }
        h.now += 400_000
        repo.refresh()
        assertFalse(repo.flags.value.isEnabled(FeatureKey.ASSISTANT))
    }

    @Test
    fun `lo guardado hace mas de 72 horas se ignora`() = runTest {
        val h = Harness({ HttpStatusCode.OK to assistantOff })
        h.repo().refresh()

        h.response = { HttpStatusCode.InternalServerError to "{}" }
        h.now += 73L * 3600 * 1000
        val reopened = h.repo()
        reopened.refresh()

        assertTrue(reopened.flags.value.isEnabled(FeatureKey.ASSISTANT))
    }

    @Test
    fun `lo guardado hace menos de 72 horas sigue valiendo`() = runTest {
        val h = Harness({ HttpStatusCode.OK to assistantOff })
        h.repo().refresh()
        h.now += 71L * 3600 * 1000
        assertFalse(h.repo().flags.value.isEnabled(FeatureKey.ASSISTANT))
    }

    @Test
    fun `no pregunta al servidor mas de una vez por refreshAfterSeconds`() = runTest {
        val h = Harness({ HttpStatusCode.OK to assistantOff })
        val repo = h.repo()
        repo.refresh()
        h.now += 299_000
        repo.refresh()
        assertEquals(1, h.calls)
        h.now += 2_000
        repo.refresh()
        assertEquals(2, h.calls)
    }

    @Test
    fun `un 503 FEATURE_DISABLED se convierte en la excepcion con clave y mensaje`() = runTest {
        val body = """{"code":"FEATURE_DISABLED","feature":"meetup_create","message":"En pausa"}"""
        val engine = MockEngine { respond(body, HttpStatusCode.ServiceUnavailable, jsonHeaders) }
        val client = buildApiHttpClient("https://x/", engine) { null }

        val e = assertFailsWith<FeatureDisabledException> { client.post("meetups") }

        assertEquals("meetup_create", e.key)
        assertEquals("En pausa", e.message)
    }

    @Test
    fun `un 503 cualquiera NO es FeatureDisabledException`() = runTest {
        val engine = MockEngine { respond("{}", HttpStatusCode.ServiceUnavailable, jsonHeaders) }
        val client = buildApiHttpClient("https://x/", engine) { null }

        val e = runCatching { client.post("meetups") }.exceptionOrNull()

        assertTrue(e != null && e !is FeatureDisabledException)
    }
}
