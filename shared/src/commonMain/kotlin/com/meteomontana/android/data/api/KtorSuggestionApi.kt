package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.SuggestionRequest
import com.meteomontana.android.data.api.dto.SuggestionResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/** Botón "?" de ayuda → "Sugerir algo / reportar un fallo". */
class KtorSuggestionApi(private val client: HttpClient) {

    @Throws(Exception::class)
    suspend fun submit(req: SuggestionRequest): SuggestionResponse =
        client.post("suggestions") { setBody(req) }.body()
}
