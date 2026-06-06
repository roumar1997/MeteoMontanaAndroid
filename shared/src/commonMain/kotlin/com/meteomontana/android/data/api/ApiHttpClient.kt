package com.meteomontana.android.data.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Crea el HttpClient Ktor compartido para todas las APIs.
 * El engine (OkHttp en Android, Darwin en iOS) se inyecta vía classpath.
 *
 * @param baseUrl URL base incluido el trailing slash, e.g. "http://10.0.2.2:8080/api/"
 * @param tokenProvider suspending function que devuelve el Bearer token actual (Firebase ID token)
 */
fun buildApiHttpClient(
    baseUrl: String,
    tokenProvider: suspend () -> String?
): HttpClient = HttpClient {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        })
    }
    install(Auth) {
        bearer {
            loadTokens {
                val token = tokenProvider() ?: return@loadTokens null
                BearerTokens(token, "")
            }
            refreshTokens { null } // no refresh — Firebase tokens se obtienen frescos cada vez
        }
    }
    defaultRequest {
        url(baseUrl)
        contentType(ContentType.Application.Json)
    }
}
