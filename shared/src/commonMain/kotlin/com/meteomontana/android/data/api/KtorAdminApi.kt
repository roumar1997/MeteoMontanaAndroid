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
import io.ktor.client.request.setBody

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

    suspend fun approveContribution(id: String): ContributionDto =
        client.post("admin/contributions/$id/approve").body()

    suspend fun rejectContribution(id: String, req: RejectReason): ContributionDto =
        client.post("admin/contributions/$id/reject") { setBody(req) }.body()
}
