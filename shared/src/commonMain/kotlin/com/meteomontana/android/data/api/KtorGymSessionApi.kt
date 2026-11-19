package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.GymSessionDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Entrenos en rocodromo (v1.1). Estas rutas aun NO existen en el servidor (ver BACKEND-PENDIENTE.md):
 * el repositorio local las intenta y, si fallan, sigue con la copia del movil.
 */
class KtorGymSessionApi(private val client: HttpClient) {

    @Throws(Exception::class)
    suspend fun checkInToday(gymId: Long, source: String): GymSessionDto =
        client.post("gyms/$gymId/sessions/today") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("source" to source))
        }.body()

    @Throws(Exception::class)
    suspend fun undoToday(gymId: Long) {
        client.delete("gyms/$gymId/sessions/today")
    }

    /** Mis entrenos desde [since] (yyyy-MM-dd). */
    @Throws(Exception::class)
    suspend fun mine(since: String): List<GymSessionDto> =
        client.get("gyms/sessions/mine") { parameter("since", since) }.body()
}
