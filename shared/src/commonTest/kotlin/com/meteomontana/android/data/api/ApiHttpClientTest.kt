package com.meteomontana.android.data.api

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.ContentType
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Comportamiento del cliente ante el token de Firebase, con un motor de red
 * FALSO (no toca internet).
 *
 * Por qué existe: acotar la espera del token arregló "entras en una escuela y no
 * carga nada", pero abría un riesgo — que una petición que SÍ necesita identidad
 * saliera sin token y fallara en silencio. Cambiar un cuelgue por un fallo mudo
 * no sería un arreglo. Aquí se fija que eso NO pasa.
 */
class ApiHttpClientTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun `adjunta el token cuando Firebase responde a tiempo`() = runTest {
        var recibido: String? = null
        val engine = MockEngine { req ->
            recibido = req.headers[HttpHeaders.Authorization]
            respond("{}", HttpStatusCode.OK, jsonHeaders)
        }
        val client = buildApiHttpClient("https://x/", engine) { "tok" }

        client.get("algo")

        assertEquals("Bearer tok", recibido)
    }

    @Test
    fun `si Firebase tarda, la peticion SALE igual (sin token) y no se cuelga`() = runTest {
        // El caso del bug: en frío el SDK aún arranca. Antes la petición ni
        // salía; ahora sale sin token y el backend sirve lo que es público.
        var llego = false
        val engine = MockEngine {
            llego = true
            respond("{}", HttpStatusCode.OK, jsonHeaders)
        }
        val client = buildApiHttpClient("https://x/", engine) {
            delay(TOKEN_WAIT_MS * 10)
            "tarde"
        }

        client.get("publico")

        assertTrue(llego, "la petición tenía que salir aunque no hubiera token")
    }

    @Test
    fun `ante 401 sin token, pide el token SIN prisa y reintenta una vez`() = runTest {
        // La red de seguridad: una ruta que exige identidad no puede quedarse
        // sin cargar solo porque Firebase fuera lento el primer segundo.
        var intentos = 0
        val autorizaciones = mutableListOf<String?>()
        var yaHayToken = false
        val engine = MockEngine { req ->
            intentos++
            autorizaciones += req.headers[HttpHeaders.Authorization]
            if (req.headers[HttpHeaders.Authorization] == null)
                respond("", HttpStatusCode.Unauthorized)
            else respond("{}", HttpStatusCode.OK, jsonHeaders)
        }
        val client = buildApiHttpClient("https://x/", engine) {
            // 1ª llamada: Firebase aún no está listo → tarda demasiado.
            // 2ª (la del reintento, sin límite): ya responde.
            if (!yaHayToken) { yaHayToken = true; delay(TOKEN_WAIT_MS * 10); null }
            else "tok-tardio"
        }

        val res: HttpResponse = client.get("privado")

        assertEquals(2, intentos, "tenía que reintentar exactamente una vez")
        assertNull(autorizaciones[0], "el 1er intento sale sin token")
        assertEquals("Bearer tok-tardio", autorizaciones[1], "el reintento ya lo lleva")
        assertEquals(HttpStatusCode.OK, res.status)
    }

    @Test
    fun `sin sesion no reintenta en bucle`() = runTest {
        // Usuario no logueado: el 401 es correcto y definitivo. Reintentar sin
        // parar seria peor que el fallo.
        var intentos = 0
        val engine = MockEngine {
            intentos++
            respond("", HttpStatusCode.Unauthorized)
        }
        val client = buildApiHttpClient("https://x/", engine) { null }

        runCatching { client.get("privado") }

        assertEquals(1, intentos, "sin token que ofrecer, no se reintenta")
    }
}
