package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.AdminLogDto
import com.meteomontana.android.data.api.dto.AdminPushRequest
import com.meteomontana.android.data.api.dto.AdminPushResponse
import com.meteomontana.android.data.api.dto.AdminStatsDto
import com.meteomontana.android.data.api.dto.ContributionDto
import com.meteomontana.android.data.api.dto.RejectReason
import com.meteomontana.android.data.api.dto.SubmissionDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

class KtorAdminApi(private val client: HttpClient) {

    suspend fun stats(): AdminStatsDto = client.get("admin/stats").body()

    suspend fun pendingSubmissions(status: String? = null): List<SubmissionDto> =
        client.get("admin/submissions") {
            if (status != null) url.parameters.append("status", status)
        }.body()

    suspend fun approve(id: String): SubmissionDto =
        client.post("admin/submissions/$id/approve").body()

    suspend fun reject(id: String, req: RejectReason): SubmissionDto =
        client.post("admin/submissions/$id/reject") { setBody(req) }.body()

    /** "Esperando respuesta": saca (o mete) la propuesta de escuela de la cola
     *  normal mientras el admin espera contestación del proponente. */
    suspend fun setSubmissionAwaitingReply(id: String, waiting: Boolean) {
        client.post("admin/submissions/$id/awaiting-reply") { setBody(AwaitingReplyRequest(waiting)) }
    }

    suspend fun logs(limit: Int = 100): List<AdminLogDto> =
        client.get("admin/logs") { parameter("limit", limit) }.body()

    suspend fun sendPush(req: AdminPushRequest): AdminPushResponse =
        client.post("admin/push") { setBody(req) }.body()

    suspend fun pendingContributions(status: String? = null): List<ContributionDto> =
        client.get("admin/contributions") {
            if (status != null) url.parameters.append("status", status)
        }.body()

    /** [editedBloquesJson] != null = "EDITAR Y APROBAR": se aprueba con la
     *  versión retocada por el admin. */
    suspend fun approveContribution(id: String, editedBloquesJson: String? = null): ContributionDto =
        client.post("admin/contributions/$id/approve") {
            if (editedBloquesJson != null) setBody(mapOf("bloquesJson" to editedBloquesJson))
        }.body()

    suspend fun rejectContribution(id: String, req: RejectReason): ContributionDto =
        client.post("admin/contributions/$id/reject") { setBody(req) }.body()

    /** "Esperando respuesta" — espejo del de escuelas nuevas, para propuestas
     *  de mejora (piedras, sectores, parkings, correcciones...). */
    suspend fun setContributionAwaitingReply(id: String, waiting: Boolean) {
        client.post("admin/contributions/$id/awaiting-reply") { setBody(AwaitingReplyRequest(waiting)) }
    }

    suspend fun moveSchool(schoolId: String, lat: Double, lon: Double) {
        client.put("admin/schools/$schoolId/position") {
            setBody(MoveSchoolRequest(lat, lon))
        }
    }

    /** Historial de un usuario (escuelas + mejoras propuestas), para que el
     *  admin vea de un vistazo si ya ha mandado cosas antes. */
    suspend fun userActivity(uid: String): List<UserActivityItemDto> =
        client.get("admin/users/$uid/activity").body()

    suspend fun getPendingReports(): List<MeetupReportDto> =
        client.get("admin/reports").body()

    suspend fun resolveReport(id: String, action: String): MeetupReportDto =
        client.post("admin/reports/$id/resolve") {
            contentType(ContentType.Application.Json)
            setBody(ResolveReportRequest(action))
        }.body()
}

@Serializable
data class MoveSchoolRequest(val lat: Double, val lon: Double)

@Serializable
data class AwaitingReplyRequest(val waiting: Boolean)

@Serializable
data class MeetupReportDto(
    val id: String,
    val meetupId: String,
    val reporterUid: String,
    val reportedUid: String? = null,
    val reason: String,
    val context: String? = null,
    val status: String,           // PENDING | RESOLVED | DISMISSED
    val resolvedBy: String? = null,
    val createdAt: String
)

@Serializable
data class ResolveReportRequest(val action: String)  // "resolve" | "dismiss"

@Serializable
data class UserActivityItemDto(
    val id: String,
    val kind: String,    // "SCHOOL" | tipo de PendingContribution
    val label: String?,
    val status: String,
    val createdAt: String
)
