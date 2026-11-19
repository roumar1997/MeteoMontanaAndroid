package com.meteomontana.android.gym

import com.meteomontana.android.data.api.CLIENT_FEATURES
import com.meteomontana.android.data.api.CLIENT_FEATURES_HEADER
import com.meteomontana.android.data.api.KtorGymApi
import com.meteomontana.android.data.api.buildApiHttpClient
import com.meteomontana.android.data.cache.KeyValueGymCatalogCache
import com.meteomontana.android.data.repository.KtorGymRepository
import com.meteomontana.android.domain.model.FeatureFlagState
import com.meteomontana.android.domain.model.FeatureFlags
import com.meteomontana.android.domain.model.FeatureKey
import com.meteomontana.android.domain.model.gym.GymDiscipline
import com.meteomontana.android.domain.model.gym.GymKind
import com.meteomontana.android.domain.model.gym.GymVerification
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GymRepositoryTest {

    private class MemoryStore : KeyValueStore {
        val map = mutableMapOf<String, String>()
        override fun getString(key: String) = map[key]
        override fun putString(key: String, value: String) { map[key] = value }
    }

    private val json = """
        [{"id":5,"slug":"a","name":"Sputnik","chain":"Sputnik","kind":"COMMERCIAL","discipline":"BOTH",
          "region":"Madrid","province":"Madrid","city":"Madrid","lat":40.4,"lon":-3.7,"outdoor":false,
          "openingHours":[{"days":"MON-FRI","open":"10:00","close":"23:00"}],"priceSingleEur":12.5,
          "verification":"VERIFIED","hidden":false,"updatedAt":"2026-11-19T10:00:00Z","campoNuevo":123},
         {"id":6,"slug":"b","name":"Raro","kind":"ALGO_NUEVO","discipline":"ALGO_NUEVO","verification":"???",
          "lat":36.5,"lon":-6.2,"hasAutobelay":null},
         {"id":7,"slug":"c","name":"Oculto","kind":"FREE","lat":1.0,"lon":1.0,"hidden":true}]
    """.trimIndent()

    private class Harness(var status: HttpStatusCode, val body: String, val store: MemoryStore = MemoryStore()) {
        val sentIfNoneMatch = mutableListOf<String?>()
        val sentFeatures = mutableListOf<String?>()
        val engine = MockEngine { req ->
            sentIfNoneMatch += req.headers[HttpHeaders.IfNoneMatch]
            sentFeatures += req.headers[CLIENT_FEATURES_HEADER]
            when {
                status == HttpStatusCode.NotModified -> respond("", status)
                status.value >= 400 -> respond("{}", status, jsonHeaders)
                else -> respond(body, status, headersOf(
                    HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                    HttpHeaders.ETag to listOf("\"v1\"")
                ))
            }
        }
        fun repo() = KtorGymRepository(
            KtorGymApi(buildApiHttpClient("https://x/", engine) { null }),
            KeyValueGymCatalogCache(store)
        )
        companion object {
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        }
    }

    @Test
    fun `descarga el catalogo, tolera enums y campos nuevos, y esconde las bajas`() = runTest {
        val h = Harness(HttpStatusCode.OK, json)
        val gyms = h.repo().getGyms()
        assertEquals(listOf(5L, 6L), gyms.map { it.id })
        val full = gyms[0]
        assertEquals(GymKind.COMMERCIAL, full.kind)
        assertEquals(GymVerification.VERIFIED, full.verification)
        assertEquals(12.5, full.priceSingleEur)
        assertEquals(1, full.openingHours?.size)
        val raro = gyms[1]
        assertEquals(GymKind.COMMERCIAL, raro.kind)             // enum desconocido -> valor por defecto
        assertEquals(GymDiscipline.UNKNOWN, raro.discipline)
        assertEquals(GymVerification.UNCONFIRMED, raro.verification)
        assertNull(raro.hasAutobelay)
        assertNull(raro.openingHours)
    }

    @Test
    fun `la segunda vez manda el ETag y un 304 usa la copia`() = runTest {
        val h = Harness(HttpStatusCode.OK, json)
        h.repo().getGyms()
        h.status = HttpStatusCode.NotModified
        val again = h.repo().getGyms()
        assertEquals(listOf(null, "\"v1\""), h.sentIfNoneMatch)
        assertEquals(listOf(5L, 6L), again.map { it.id })
    }

    @Test
    fun `sin red devuelve la copia local`() = runTest {
        val h = Harness(HttpStatusCode.OK, json)
        h.repo().getGyms()
        h.status = HttpStatusCode.InternalServerError
        assertEquals(listOf(5L, 6L), h.repo().getGyms().map { it.id })
        assertEquals(listOf(5L, 6L), h.repo().cachedGyms().map { it.id })
    }

    @Test
    fun `sin red y sin copia lanza`() = runTest {
        val h = Harness(HttpStatusCode.InternalServerError, "{}")
        assertTrue(h.repo().cachedGyms().isEmpty())
        assertFailsWith<Exception> { h.repo().getGyms() }
    }

    @Test
    fun `una copia ilegible se ignora`() = runTest {
        val store = MemoryStore().also { it.map["gym_catalog_v1"] = "{basura" }
        val h = Harness(HttpStatusCode.OK, json, store)
        assertTrue(h.repo().cachedGyms().isEmpty())
        assertEquals(2, h.repo().getGyms().size)
    }

    @Test
    fun `cada peticion lleva la cabecera de capacidades`() = runTest {
        val h = Harness(HttpStatusCode.OK, json)
        h.repo().getGyms()
        assertEquals(listOf<String?>(CLIENT_FEATURES), h.sentFeatures)
        assertEquals("gyms", CLIENT_FEATURES)
        assertEquals("X-Client-Features", CLIENT_FEATURES_HEADER)
    }

    @Test
    fun `el interruptor de rocodromos esta apagado si el servidor no dice nada`() {
        assertFalse(FeatureFlags.ALL_ON.isGymsEnabled)
        assertFalse(FeatureFlags(mapOf("assistant" to FeatureFlagState(true))).isGymsEnabled)
        assertFalse(FeatureFlags(mapOf(FeatureKey.GYMS to FeatureFlagState(false))).isGymsEnabled)
        assertTrue(FeatureFlags(mapOf(FeatureKey.GYMS to FeatureFlagState(true))).isGymsEnabled)
        // El resto de funciones siguen encendidas por defecto.
        assertTrue(FeatureFlags.ALL_ON.isEnabled(FeatureKey.ASSISTANT))
    }
}
