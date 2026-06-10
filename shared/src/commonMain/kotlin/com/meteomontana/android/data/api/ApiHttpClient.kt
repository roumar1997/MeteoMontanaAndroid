package com.meteomontana.android.data.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.createClientPlugin
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
