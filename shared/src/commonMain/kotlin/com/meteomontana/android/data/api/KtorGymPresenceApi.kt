package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.SchoolPresenceDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put

/** "Quien va hoy" en un rocodromo. Mismo contrato (y mismo DTO) que la presencia de escuelas. */
class KtorGymPresenceApi(private val client: HttpClient) {

    @Throws(Exception::class)
    suspend fun getActivePresence(gymId: Long): List<SchoolPresenceDto> =
        client.get("gyms/$gymId/presence").body()

    @Throws(Exception::class)
    suspend fun markPresence(gymId: Long): SchoolPresenceDto =
        client.put("gyms/$gymId/presence").body()

    @Throws(Exception::class)
    suspend fun clearPresence(gymId: Long) {
        client.delete("gyms/$gymId/presence")
    }
}
