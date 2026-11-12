package com.meteomontana.android.data

import com.meteomontana.android.data.api.KtorTitleEffectApi
import com.meteomontana.android.data.api.buildApiHttpClient
import com.meteomontana.android.data.repository.TitleEffectRepositoryImpl
import com.meteomontana.android.domain.model.TitleEffectSpec
import com.meteomontana.android.domain.port.KeyValueStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TitleEffectsTest {

    private val catalogJson = """
        {"effects":[{"key":"UNICORNS","name":"Unicornios","orientation":"ORBIT","figureCount":3,
          "sprites":[{"url":"https://photos.climbingteams.com/effects/u/1.png","facesRight":true}],
          "ringColors":["#FF00FF"],"glowColor":"#FF00FF","highlightColor":"#FFFFFF","particleColor":"#FFFFFF",
          "particleStyle":"SPARKLE","motion":{"orbit":1.5,"size":0.9,"speed":0.4,"staticAngles":[-2.0],"staticSize":0.6},
          "campoNuevo":true}],"refreshAfterSeconds":3600}
    """.trimIndent()

    private class MemoryStore : KeyValueStore {
        val map = mutableMapOf<String, String>()
        override fun getString(key: String) = map[key]
        override fun putString(key: String, value: String) { map[key] = value }
    }

    private class Harness(var response: () -> Pair<HttpStatusCode, String>) {
        val store = MemoryStore()
        var now = 1_000_000L
        var calls = 0
        private val engine = MockEngine { _ ->
            calls++
            val (status, body) = response()
            respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        val api = KtorTitleEffectApi(buildApiHttpClient("https://x/", engine) { null })
        fun repo() = TitleEffectRepositoryImpl(api, store) { now }
    }

    @Test
    fun `sin nada guardado ni red se ven los integrados`() = runTest {
        val h = Harness { HttpStatusCode.InternalServerError to "{}" }
        val repo = h.repo()
        repo.refresh()
        assertNotNull(repo.catalog.value.find("fairies"))
        assertNotNull(repo.catalog.value.find("LOLA"))
        assertNull(repo.catalog.value.find("UNICORNS"))
    }

    @Test
    fun `un efecto nuevo del servidor se guarda y se ve sin red en el siguiente arranque`() = runTest {
        val h = Harness { HttpStatusCode.OK to catalogJson }
        h.repo().refresh()
        h.response = { HttpStatusCode.ServiceUnavailable to "{}" }
        val reopened = h.repo()
        val unicorns = reopened.catalog.value.find("unicorns")
        assertNotNull(unicorns)
        assertTrue(unicorns.orbits)
        assertEquals(3, unicorns.figureCount)
        // Los integrados siguen aunque el servidor no los mande.
        assertNotNull(reopened.catalog.value.find("DRAGONS"))
        assertEquals(TitleEffectSpec.EMBER, reopened.catalog.value.find("DRAGONS")?.particleStyle)
    }

    @Test
    fun `respeta refreshAfterSeconds entre arranques`() = runTest {
        val h = Harness { HttpStatusCode.OK to catalogJson }
        h.repo().refresh()
        h.now += 60_000
        h.repo().refresh()
        assertEquals(1, h.calls)
        h.now += 3_600_000
        h.repo().refresh()
        assertEquals(2, h.calls)
    }
}
