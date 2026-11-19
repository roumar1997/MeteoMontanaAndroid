package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.GymAdminUpdateDto
import com.meteomontana.android.data.api.dto.GymDto
import com.meteomontana.android.data.api.dto.GymReportDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

/** Cola de reportes de rocodromos y edicion de fichas. Solo admin (el servidor lo comprueba). */
class KtorGymAdminApi(private val client: HttpClient) {

    @Throws(Exception::class)
    suspend fun pendingReports(): List<GymReportDto> = client.get("admin/gyms/reports").body()

    @Throws(Exception::class)
    suspend fun applyReport(id: Long): GymReportDto = client.post("admin/gyms/reports/$id/apply").body()

    @Throws(Exception::class)
    suspend fun rejectReport(id: Long): GymReportDto = client.post("admin/gyms/reports/$id/reject").body()

    @Throws(Exception::class)
    suspend fun editGym(id: Long, body: GymAdminUpdateDto): GymDto = client.put("admin/gyms/$id") { setBody(body) }.body()
}
