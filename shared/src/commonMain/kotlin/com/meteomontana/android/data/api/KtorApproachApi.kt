package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.ApproachDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

class KtorApproachApi(private val client: HttpClient) {

    @Throws(Exception::class)
    suspend fun getApproaches(schoolId: String): List<ApproachDto> =
        client.get("schools/$schoolId/approaches").body()
}
