package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.CreateMeetupRequestDto
import com.meteomontana.android.data.api.dto.MeetupAlertDto
import com.meteomontana.android.data.api.dto.MeetupDto
import com.meteomontana.android.data.api.dto.ReportRequestDto
import com.meteomontana.android.data.api.dto.SetAlertRequestDto
import com.meteomontana.android.data.api.dto.UpdateMeetupRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class KtorMeetupApi(private val client: HttpClient) {

    suspend fun getMeetups(schoolId: String? = null, date: String? = null,
                           relation: String? = null): List<MeetupDto> =
        client.get("meetups") {
            schoolId?.let { parameter("schoolId", it) }
            date?.let { parameter("date", it) }
            relation?.let { parameter("relation", it) }
        }.body()

    suspend fun getMeetup(id: String): MeetupDto = client.get("meetups/$id").body()

    suspend fun getMeetupByConversation(conversationId: String): MeetupDto? =
        client.get("meetups/by-conversation/$conversationId").body()

    suspend fun updateMeetup(id: String, description: String?): MeetupDto =
        client.patch("meetups/$id") {
            contentType(ContentType.Application.Json)
            setBody(UpdateMeetupRequestDto(description))
        }.body()

    suspend fun createMeetup(req: CreateMeetupRequestDto): MeetupDto =
        client.post("meetups") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()

    suspend fun joinMeetup(id: String, invite: String? = null): MeetupDto =
        client.post("meetups/$id/join") {
            invite?.let { parameter("invite", it) }
        }.body()

    /** Enlace de invitación al grupo (solo miembros). */
    suspend fun getInviteLink(id: String): String {
        val resp: Map<String, String> = client.get("meetups/$id/invite").body()
        return resp["link"] ?: ""
    }

    suspend fun leaveMeetup(id: String) { client.post("meetups/$id/leave") }

    suspend fun deleteMeetup(id: String) { client.delete("meetups/$id") }

    suspend fun kickMember(meetupId: String, targetUid: String) {
        client.post("meetups/$meetupId/kick") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("uid" to targetUid))
        }
    }

    suspend fun updateMyGear(meetupId: String, gearJson: String): MeetupDto =
        client.put("meetups/$meetupId/my-gear") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("gearJson" to gearJson))
        }.body()

    // Denunciar es idempotente de cara al usuario: si ya la denunció el backend
    // responde 409 y Ktor (expectSuccess) lanza. Esa excepción NO declarada
    // @Throws CRASHEA iOS (Kotlin/Native aborta al cruzar a Swift) y en Android
    // caía en un catch mudo ("no pasa nada"). La tragamos: denunciar dos veces
    // = ya está denunciado, se muestra la misma confirmación.
    suspend fun reportMeetup(meetupId: String, req: ReportRequestDto) {
        try {
            client.post("meetups/$meetupId/report") {
                contentType(ContentType.Application.Json)
                setBody(req)
            }
        } catch (_: Throwable) { /* ya denunciada / sin red: idempotente */ }
    }

    // OJO: si el usuario NO tiene alerta configurada, el backend devuelve un
    // body vacío y Ktor lanza NoTransformationFoundException al deserializar.
    // En Kotlin/Native una excepción no declarada @Throws CRASHEA la app (no
    // se propaga al catch de Swift). Por eso la capturamos aquí y devolvemos
    // null = "sin alerta" (el use case ya lo interpreta como enabled=false).
    suspend fun getMeetupAlert(): MeetupAlertDto? =
        try { client.get("meetups/alerts/me").body() }
        catch (e: Exception) { null }

    suspend fun setMeetupAlert(req: SetAlertRequestDto): MeetupAlertDto =
        client.put("meetups/alerts/me") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()
}
