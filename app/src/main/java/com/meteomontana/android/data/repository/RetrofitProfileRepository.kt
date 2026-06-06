package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.ProfileApi
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.PrivateProfile
import com.meteomontana.android.domain.repository.ProfileRepository
import javax.inject.Inject

class RetrofitProfileRepository @Inject constructor(
    private val api: ProfileApi
) : ProfileRepository {
    override suspend fun getMyProfile(): PrivateProfile = api.getMyProfile().toDomain()
}
