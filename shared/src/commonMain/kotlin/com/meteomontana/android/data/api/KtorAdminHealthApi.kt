package com.meteomontana.android.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable

@Serializable
data class EndpointErrorsDto(val endpoint: String, val count: Int, val last: String? = null, val lastSummary: String? = null)

@Serializable
data class FailureDto(val endpoint: String, val status: Int = 500, val summary: String = "", val at: String = "")

@Serializable
data class LatencyDto(val endpoint: String, val calls: Long = 0, val avgMs: Long = 0, val maxMs: Long = 0)

@Serializable
data class AdminHealthDto(
    val uptimeMinutes: Long = 0,
    val errors24h: Int = 0,
    val topErrors: List<EndpointErrorsDto> = emptyList(),
    val activeUsersToday: Int = 0,
    val assistantUsedToday: Int = -1,
    val assistantDailyLimit: Int = -1,
    val pendingContributions: Int = -1,
    val checkedAt: String = "",
    val recentFailures: List<FailureDto> = emptyList(),
    val slowest: List<LatencyDto> = emptyList(),
)

class KtorAdminHealthApi(private val client: HttpClient) {
    suspend fun health(): AdminHealthDto = client.get("admin/health").body()
}
