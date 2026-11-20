package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.GymAscentStatsDto
import com.meteomontana.android.data.api.dto.GymAscentsRequestDto
import com.meteomontana.android.data.api.dto.GymSessionDto
import com.meteomontana.android.data.api.dto.GymStatsDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

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

    /** Sustituye lo hecho en una sesion propia (404 si es ajena); [] lo borra. Devuelve la sesion. */
    @Throws(Exception::class)
    suspend fun putAscents(sessionId: Long, body: GymAscentsRequestDto): GymSessionDto =
        client.put("gyms/sessions/$sessionId/ascents") { setBody(body) }.body()

    /** Borra una sesion propia con todo lo hecho en ella (404 si es ajena). */
    @Throws(Exception::class)
    suspend fun deleteSession(sessionId: Long) {
        client.delete("gyms/sessions/$sessionId")
    }

    /** Ascensiones por color o grado en un periodo (misma privacidad que gym-stats). */
    @Throws(Exception::class)
    suspend fun ascentStats(uidOrUsername: String, period: String, date: String, gymId: Long?): GymAscentStatsDto =
        client.get("users/$uidOrUsername/gym-ascents") {
            parameter("period", period)
            parameter("date", date)
            gymId?.let { parameter("gymId", it) }
        }.body()

    /** Todo el historial de rocódromo de una persona (404 si su perfil es privado y no la sigues). */
    @Throws(Exception::class)
    suspend fun summary(uidOrUsername: String): com.meteomontana.android.data.api.dto.GymSummaryDto =
        client.get("users/$uidOrUsername/gym-summary").body()

    @Throws(Exception::class)
    suspend fun stats(uidOrUsername: String, year: Int): GymStatsDto =
        client.get("users/$uidOrUsername/gym-stats") { parameter("year", year) }.body()
}
