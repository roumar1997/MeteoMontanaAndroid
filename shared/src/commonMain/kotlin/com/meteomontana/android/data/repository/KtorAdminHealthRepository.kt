package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorAdminHealthApi
import com.meteomontana.android.domain.repository.AdminHealthRepository
import com.meteomontana.android.domain.usecase.admin.AdminHealth
import com.meteomontana.android.domain.usecase.admin.EndpointErrors

class KtorAdminHealthRepository(private val api: KtorAdminHealthApi) : AdminHealthRepository {
    override suspend fun health(): AdminHealth = api.health().let { h ->
        AdminHealth(h.uptimeMinutes, h.errors24h, h.topErrors.map { EndpointErrors(it.endpoint, it.count, it.last) },
            h.activeUsersToday, h.assistantUsedToday, h.assistantDailyLimit, h.pendingContributions, h.checkedAt)
    }
}
