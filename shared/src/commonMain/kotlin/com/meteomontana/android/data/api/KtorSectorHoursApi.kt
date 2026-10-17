package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.SectorHoursResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class KtorSectorHoursApi(private val client: HttpClient) {

    /** GET /api/schools/{id}/sector-hours?date= — público. */
    suspend fun get(schoolId: String, date: String?): SectorHoursResponseDto =
        client.get("schools/$schoolId/sector-hours") { date?.let { parameter("date", it) } }.body()
}
