package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.SchoolPresenceDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post

class KtorSchoolPresenceApi(private val client: HttpClient) {

    @Throws(Exception::class)
    suspend fun getActivePresence(schoolId: String): List<SchoolPresenceDto> =
        client.get("schools/$schoolId/presence").body()

    @Throws(Exception::class)
    suspend fun markPresence(schoolId: String): SchoolPresenceDto =
        client.post("schools/$schoolId/presence").body()

    @Throws(Exception::class)
    suspend fun clearPresence(schoolId: String) {
        client.delete("schools/$schoolId/presence")
    }
}
