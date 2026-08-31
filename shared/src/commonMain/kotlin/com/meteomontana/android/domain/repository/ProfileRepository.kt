package com.meteomontana.android.domain.repository

import com.meteomontana.android.data.api.dto.FcmTokenRequest
import com.meteomontana.android.data.api.dto.UpdateProfileRequest
import com.meteomontana.android.domain.model.PrivateProfile

interface ProfileRepository {
    suspend fun getMyProfile(): PrivateProfile
    suspend fun updateMyProfile(req: UpdateProfileRequest): PrivateProfile
    suspend fun updateFcmToken(req: FcmTokenRequest)
    suspend fun deleteMyAccount()

    // ── Alerta de tiempo (fin de semana / ventana óptima) ──
    suspend fun getWeekendAlert(): com.meteomontana.android.domain.model.WeekendAlert
    suspend fun updateWeekendAlert(
        alert: com.meteomontana.android.domain.model.WeekendAlert
    ): com.meteomontana.android.domain.model.WeekendAlert
}
