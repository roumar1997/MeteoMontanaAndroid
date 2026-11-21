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

    /** Apunta el entreno de [date] (yyyy-MM-dd; no futura ni de hace mas de un ano). Idempotente por dia: reintentar no duplica. */
    @Throws(Exception::class)
    suspend fun checkInOn(gymId: Long, date: String): GymSessionDto =
        client.post("gyms/$gymId/sessions") { setBody(com.meteomontana.android.data.api.dto.GymSessionDateRequestDto(date)) }.body()

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

    /** Mi rocódromo habitual en el servidor. */
    @Throws(Exception::class)
    suspend fun homeGym(): com.meteomontana.android.data.api.dto.HomeGymResponseDto = client.get("me/home-gym").body()

    /** Fija mi rocódromo habitual; [gymId] null lo quita. */
    @Throws(Exception::class)
    suspend fun putHomeGym(gymId: Long?): com.meteomontana.android.data.api.dto.HomeGymResponseDto =
        client.put("me/home-gym") { setBody(com.meteomontana.android.data.api.dto.HomeGymRequestDto(gymId)) }.body()

    /** Mi orden de categorias de Ascensiones en el servidor. */
    @Throws(Exception::class)
    suspend fun ascentOrder(): com.meteomontana.android.data.api.dto.AscentOrderDto = client.get("me/ascent-order").body()

    /** Guarda mi orden de categorias de Ascensiones («BOARDS,BLOCKS,ROUTES»); los demas lo ven en mi resumen. */
    @Throws(Exception::class)
    suspend fun putAscentOrder(order: String): com.meteomontana.android.data.api.dto.AscentOrderDto =
        client.put("me/ascent-order") { setBody(com.meteomontana.android.data.api.dto.AscentOrderDto(order)) }.body()

    /** Mi orden de rocodromos en el servidor. */
    @Throws(Exception::class)
    suspend fun gymOrder(): com.meteomontana.android.data.api.dto.GymOrderDto = client.get("me/gym-order").body()

    /** Guarda mi orden de rocodromos (ids «5,3,9»); los demas lo ven en mi resumen. */
    @Throws(Exception::class)
    suspend fun putGymOrder(order: String): com.meteomontana.android.data.api.dto.GymOrderDto =
        client.put("me/gym-order") { setBody(com.meteomontana.android.data.api.dto.GymOrderDto(order)) }.body()

    @Throws(Exception::class)
    suspend fun stats(uidOrUsername: String, year: Int): GymStatsDto =
        client.get("users/$uidOrUsername/gym-stats") { parameter("year", year) }.body()
}
