package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorAdminHealthApi
import com.meteomontana.android.domain.repository.AdminHealthRepository
import com.meteomontana.android.domain.usecase.admin.AdminHealth
import com.meteomontana.android.domain.usecase.admin.EndpointErrors
import com.meteomontana.android.domain.usecase.admin.EndpointLatency
import com.meteomontana.android.domain.usecase.admin.FailureEvent

class KtorAdminHealthRepository(private val api: KtorAdminHealthApi) : AdminHealthRepository {
    override suspend fun health(): AdminHealth = api.health().let { h ->
        AdminHealth(h.uptimeMinutes, h.errors24h, h.topErrors.map { EndpointErrors(it.endpoint, it.count, it.last, it.lastSummary) },
            h.activeUsersToday, h.assistantUsedToday, h.assistantDailyLimit, h.pendingContributions, h.checkedAt,
            h.recentFailures.map { FailureEvent(it.endpoint, it.status, it.summary, it.at) },
            h.slowest.map { EndpointLatency(it.endpoint, it.calls, it.avgMs, it.maxMs) })
    }
}
