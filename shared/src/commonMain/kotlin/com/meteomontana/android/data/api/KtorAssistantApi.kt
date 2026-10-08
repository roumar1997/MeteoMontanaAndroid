package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.AssistantAnswerDto
import com.meteomontana.android.data.api.dto.AssistantAskRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * Asistente de búsqueda con IA. Exige sesión (token de Firebase): el cupo diario
 * es por usuario. Los estados de "cupo agotado" o "ocupado" llegan como respuesta
 * normal (200 con `status`), no como error HTTP.
 */
class KtorAssistantApi(private val client: HttpClient) {

    @Throws(Exception::class)
    suspend fun ask(req: AssistantAskRequest): AssistantAnswerDto =
        client.post("assistant/ask") { setBody(req) }.body()
}
