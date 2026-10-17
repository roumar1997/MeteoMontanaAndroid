package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.RestrictionDto
import com.meteomontana.android.data.api.dto.RestrictionInputDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

class KtorRestrictionApi(private val client: HttpClient) {

    /** GET /api/restrictions/active — público. */
    suspend fun active(): List<RestrictionDto> = client.get("restrictions/active").body()

    suspend fun bySchool(schoolId: String): List<RestrictionDto> = client.get("schools/$schoolId/restrictions").body()

    suspend fun adminList(): List<RestrictionDto> = client.get("admin/restrictions").body()

    suspend fun adminCreate(req: RestrictionInputDto): RestrictionDto =
        client.post("admin/restrictions") { setBody(req) }.body()

    suspend fun adminUpdate(id: String, req: RestrictionInputDto): RestrictionDto =
        client.put("admin/restrictions/$id") { setBody(req) }.body()

    suspend fun adminDelete(id: String) {
        client.delete("admin/restrictions/$id")
    }
}
