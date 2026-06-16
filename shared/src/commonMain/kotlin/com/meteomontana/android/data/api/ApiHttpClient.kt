package com.meteomontana.android.data.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.Charsets
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun buildApiHttpClient(
    baseUrl: String,
    tokenProvider: suspend () -> String?
): HttpClient {
    // Plugin que adjunta el ID token de Firebase en CADA request, pidiéndoselo
    // al provider justo en el momento (no cachea). El SDK de Firebase ya refresca
    // el token internamente cuando está cerca de expirar.
    val authPlugin = createClientPlugin("FirebaseBearerAuth") {
        onRequest { request, _ ->
            if (request.headers[HttpHeaders.Authorization] == null) {
                tokenProvider()?.let {
                    request.header(HttpHeaders.Authorization, "Bearer $it")
                }
            }
        }
    }

    return HttpClient {
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
        defaultRequest {
            url(baseUrl)
            contentType(ContentType.Application.Json)
        }
    }
}
