package com.meteomontana.android.domain.usecase.admin

import com.meteomontana.android.domain.repository.AdminHealthRepository

/** Errores de un endpoint en las últimas 24 h. */
data class EndpointErrors(val endpoint: String, val count: Int, val last: String?)

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
)

class GetAdminHealthUseCase(private val repository: AdminHealthRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): AdminHealth = repository.health()
}
