package com.meteomontana.android.domain.usecase.admin

import com.meteomontana.android.domain.repository.AdminHealthRepository

/** Errores de un endpoint en las últimas 24 h. */
data class EndpointErrors(val endpoint: String, val count: Int, val last: String?, val lastSummary: String?)

/** Un fallo concreto (el más reciente primero). */
data class FailureEvent(val endpoint: String, val status: Int, val summary: String, val at: String)

/** Latencia media y máxima de un endpoint desde el último despliegue. */
data class EndpointLatency(val endpoint: String, val calls: Long, val avgMs: Long, val maxMs: Long)

/** Panel de SALUD del admin. Los -1 = el servidor no lo sabe. */
data class AdminHealth(
    val uptimeMinutes: Long,
    val errors24h: Int,
    val topErrors: List<EndpointErrors>,
    val activeUsersToday: Int,
    val assistantUsedToday: Int,
    val assistantDailyLimit: Int,
    val pendingContributions: Int,
    val checkedAt: String,
    val recentFailures: List<FailureEvent> = emptyList(),
    val slowest: List<EndpointLatency> = emptyList(),
)

class GetAdminHealthUseCase(private val repository: AdminHealthRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): AdminHealth = repository.health()
}
