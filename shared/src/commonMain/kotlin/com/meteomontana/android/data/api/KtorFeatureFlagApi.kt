package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.FeatureFlagsDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/** Interruptores de funciones (público, sin sesión). */
class KtorFeatureFlagApi(private val client: HttpClient) {
    @Throws(Exception::class)
    suspend fun get(): FeatureFlagsDto = client.get("feature-flags").body()
}
