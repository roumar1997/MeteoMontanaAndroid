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
            refreshTokens {
                val token = tokenProvider() ?: return@refreshTokens null
                BearerTokens(token, "")
            }
            sendWithoutRequest { true }
        }
    }
    defaultRequest {
        url(baseUrl)
        contentType(ContentType.Application.Json)
    }
}
