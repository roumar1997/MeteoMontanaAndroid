package com.meteomontana.android.data.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.Charsets
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.plugin
import io.ktor.http.HttpStatusCode
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/**
 * Cuánto se espera como MUCHO a que Firebase dé el token antes de mandar la
 * petición sin él. Nace del bug de "entras en una escuela y no carga nada":
 * al arrancar en frío el SDK de Firebase aún se está inicializando, y como se
 * le pedía el token en CADA petición sin límite, las llamadas se quedaban
 * paradas antes de salir — incluso las de rutas PÚBLICAS (el catálogo de
 * escuelas y sus bloques; ver `SecurityConfig` del backend), que no necesitan
 * token para nada. Se esperaba un permiso que no hacía falta.
 *
 * Con el límite, esas peticiones salen sin token y el backend las sirve igual.
 * Las que sí exigen identidad fallarán con 401 y se reintentan cuando el SDK
 * ya está listo, que es el comportamiento correcto y RÁPIDO (fallar pronto en
 * vez de colgarse).
 */
internal const val TOKEN_WAIT_MS = 1_500L

/** Timeouts de red. Sin ellos, una red que traga sin responder (portal wifi,
 *  cobertura fantasma) deja la petición colgada indefinidamente. */
private const val CONNECT_TIMEOUT_MS = 10_000L
private const val REQUEST_TIMEOUT_MS = 30_000L
private const val SOCKET_TIMEOUT_MS = 20_000L

/**
 * Token de Firebase, o null si tarda más de [TOKEN_WAIT_MS] o el provider falla.
 * NUNCA propaga la excepción: quedarse sin token debe degradar la petición
 * (sale sin identificar), no tumbarla.
 */
internal suspend fun tokenOrNull(provider: suspend () -> String?): String? =
    runCatching { withTimeoutOrNull(TOKEN_WAIT_MS) { provider() } }.getOrNull()

/**
 * @param engine solo para tests (motor falso). En la app se deja null y cada
 *   plataforma usa el suyo — OkHttp en Android, Darwin en iOS.
 */
fun buildApiHttpClient(
    baseUrl: String,
    engine: io.ktor.client.engine.HttpClientEngine? = null,
    // ÚLTIMO a propósito: así se sigue llamando con lambda final,
    // buildApiHttpClient(url) { token }, como hace el contenedor de iOS.
    tokenProvider: suspend () -> String?
): HttpClient {
    // Plugin que adjunta el ID token de Firebase en CADA request, pidiéndoselo
    // al provider justo en el momento (no cachea). El SDK de Firebase ya refresca
    // el token internamente cuando está cerca de expirar.
    // OJO: la espera está ACOTADA a propósito — ver TOKEN_WAIT_MS.
    val authPlugin = createClientPlugin("FirebaseBearerAuth") {
        onRequest { request, _ ->
            if (request.headers[HttpHeaders.Authorization] == null) {
                tokenOrNull(tokenProvider)?.let {
                    request.header(HttpHeaders.Authorization, "Bearer $it")
                }
            }
        }
    }

    return buildClient(baseUrl, authPlugin, engine).apply {
        // RED DE SEGURIDAD de la espera acotada: si una petición que SÍ necesita
        // identidad salió sin token (Firebase tardó más de TOKEN_WAIT_MS) el
        // servidor responde 401. Entonces se pide el token SIN prisa y se repite
        // una vez. Sin esto, acotar la espera arreglaba las pantallas públicas
        // pero podía dejar el diario o las favoritas sin cargar en arranque en
        // frío — cambiar un cuelgue por un fallo silencioso no es un arreglo.
        plugin(HttpSend).intercept { request ->
            val call = execute(request)
            if (call.response.status == HttpStatusCode.Unauthorized &&
                request.headers[HttpHeaders.Authorization] == null
            ) {
                val token = runCatching { tokenProvider() }.getOrNull()
                if (token != null) {
                    request.header(HttpHeaders.Authorization, "Bearer $token")
                    execute(request)
                } else call
            } else call
        }
    }
}

private fun buildClient(
    baseUrl: String,
    authPlugin: ClientPlugin<Unit>,
    engine: io.ktor.client.engine.HttpClientEngine?
): HttpClient {
    val config: io.ktor.client.HttpClientConfig<*>.() -> Unit = {
        expectSuccess = true
        // Forzar UTF-8 al decodificar respuestas: si el backend no manda
        // `charset=utf-8` en el Content-Type, Ktor podría caer a ISO-8859-1 y
        // las tildes/ñ saldrían como "??". Esto garantiza UTF-8 siempre.
        Charsets {
            register(io.ktor.utils.io.charsets.Charsets.UTF_8)
            sendCharset = io.ktor.utils.io.charsets.Charsets.UTF_8
            responseCharsetFallback = io.ktor.utils.io.charsets.Charsets.UTF_8
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            })
        }
        install(authPlugin)
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = SOCKET_TIMEOUT_MS
        }
        defaultRequest {
            url(baseUrl)
            contentType(ContentType.Application.Json)
        }
    }
    return if (engine != null) HttpClient(engine, config) else HttpClient(config)
}
