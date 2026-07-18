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

    suspend fun pendingSubmissions(): List<SubmissionDto> = client.get("admin/submissions").body()

    suspend fun approve(id: String): SubmissionDto =
        client.post("admin/submissions/$id/approve").body()

    suspend fun reject(id: String, req: RejectReason): SubmissionDto =
        client.post("admin/submissions/$id/reject") { setBody(req) }.body()

    suspend fun logs(limit: Int = 100): List<AdminLogDto> =
        client.get("admin/logs") { parameter("limit", limit) }.body()

    suspend fun sendPush(req: AdminPushRequest): AdminPushResponse =
        client.post("admin/push") { setBody(req) }.body()

    suspend fun pendingContributions(): List<ContributionDto> =
        client.get("admin/contributions").body()

    /** [editedBloquesJson] != null = "EDITAR Y APROBAR": se aprueba con la
     *  versión retocada por el admin. */
    suspend fun approveContribution(id: String, editedBloquesJson: String? = null): ContributionDto =
        client.post("admin/contributions/$id/approve") {
            if (editedBloquesJson != null) setBody(mapOf("bloquesJson" to editedBloquesJson))
        }.body()

    suspend fun rejectContribution(id: String, req: RejectReason): ContributionDto =
        client.post("admin/contributions/$id/reject") { setBody(req) }.body()

    suspend fun moveSchool(schoolId: String, lat: Double, lon: Double) {
        client.put("admin/schools/$schoolId/position") {
            setBody(MoveSchoolRequest(lat, lon))
        }
    }

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
