package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.GymSessionDto
import com.meteomontana.android.data.api.dto.GymStatsDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post

/** Entrenos en rocodromo (v1.1). Con gyms_enabled apagado el servidor contesta 503: el repositorio sigue en local. */
class KtorGymSessionApi(private val client: HttpClient) {

    /** Idempotente y sin cuerpo. Marcar «Aqui» ya apunta el entreno en el servidor (source PRESENCE). */
    @Throws(Exception::class)
    suspend fun checkInToday(gymId: Long): GymSessionDto = client.post("gyms/$gymId/sessions/today").body()

    @Throws(Exception::class)
    suspend fun undoToday(gymId: Long) {
        client.delete("gyms/$gymId/sessions/today")
    }

    /** Mis entrenos entre [from] y [to] (yyyy-MM-dd). */
    @Throws(Exception::class)
    suspend fun mine(from: String, to: String): List<GymSessionDto> =
        client.get("gyms/sessions") {
            parameter("from", from)
            parameter("to", to)
        }.body()

    @Throws(Exception::class)
    suspend fun stats(uidOrUsername: String, year: Int): GymStatsDto =
        client.get("users/$uidOrUsername/gym-stats") { parameter("year", year) }.body()
}
