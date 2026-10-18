package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.usecase.admin.AdminHealth

/** Panel de SALUD del admin. */
interface AdminHealthRepository {
    suspend fun health(): AdminHealth
}
