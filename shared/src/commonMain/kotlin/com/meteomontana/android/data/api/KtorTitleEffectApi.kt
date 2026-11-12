package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.TitleEffectsDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/** Catálogo de efectos de título (público, sin sesión). */
class KtorTitleEffectApi(private val client: HttpClient) {
    @Throws(Exception::class)
    suspend fun get(): TitleEffectsDto = client.get("title-effects").body()
}
