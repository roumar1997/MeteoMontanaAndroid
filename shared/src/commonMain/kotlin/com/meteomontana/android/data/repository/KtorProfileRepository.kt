package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorProfileApi
import com.meteomontana.android.data.api.dto.FcmTokenRequest
import com.meteomontana.android.data.api.dto.UpdateProfileRequest
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.PrivateProfile
import com.meteomontana.android.domain.model.WeekendAlert
import com.meteomontana.android.domain.repository.ProfileRepository

class KtorProfileRepository(private val api: KtorProfileApi) : ProfileRepository {

    override suspend fun getMyProfile(): PrivateProfile = api.getMyProfile().toDomain()

    override suspend fun updateMyProfile(req: UpdateProfileRequest): PrivateProfile =
        api.updateMyProfile(req).toDomain()

    override suspend fun updateFcmToken(req: FcmTokenRequest) = api.updateFcmToken(req)

    override suspend fun deleteMyAccount() = api.deleteMyAccount()

    override suspend fun getWeekendAlert(): WeekendAlert = api.getWeekendAlert().toDomain()

    override suspend fun updateWeekendAlert(alert: WeekendAlert): WeekendAlert =
        api.updateWeekendAlert(alert.toDto()).toDomain()
}

private fun com.meteomontana.android.data.api.dto.WeekendAlertDto.toDomain() = WeekendAlert(
    enabled = enabled, notifyDay = notifyDay, notifyHour = notifyHour,
    schoolIds = schoolIds, mode = mode, radiusKm = radiusKm, lat = lat, lon = lon,
    alertDays = alertDays, optimalEnabled = optimalEnabled, optimalThreshold = optimalThreshold
)

private fun WeekendAlert.toDto() = com.meteomontana.android.data.api.dto.WeekendAlertDto(
    enabled = enabled, notifyDay = notifyDay, notifyHour = notifyHour,
    schoolIds = schoolIds, mode = mode, radiusKm = radiusKm, lat = lat, lon = lon,
    alertDays = alertDays, optimalEnabled = optimalEnabled, optimalThreshold = optimalThreshold
)
