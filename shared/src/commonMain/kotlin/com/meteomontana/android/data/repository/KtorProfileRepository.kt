package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorProfileApi
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.PrivateProfile
import com.meteomontana.android.domain.repository.ProfileRepository

class KtorProfileRepository(private val api: KtorProfileApi) : ProfileRepository {

    override suspend fun getMyProfile(): PrivateProfile = api.getMyProfile().toDomain()
}
