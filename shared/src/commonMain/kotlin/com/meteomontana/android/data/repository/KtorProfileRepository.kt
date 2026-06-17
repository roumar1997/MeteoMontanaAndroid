package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorProfileApi
import com.meteomontana.android.data.api.dto.FcmTokenRequest
import com.meteomontana.android.data.api.dto.UpdateProfileRequest
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.PrivateProfile
import com.meteomontana.android.domain.repository.ProfileRepository

class KtorProfileRepository(private val api: KtorProfileApi) : ProfileRepository {

    override suspend fun getMyProfile(): PrivateProfile = api.getMyProfile().toDomain()

    override suspend fun updateMyProfile(req: UpdateProfileRequest): PrivateProfile =
        api.updateMyProfile(req).toDomain()

    override suspend fun updateFcmToken(req: FcmTokenRequest) = api.updateFcmToken(req)

    override suspend fun deleteMyAccount() = api.deleteMyAccount()
}
