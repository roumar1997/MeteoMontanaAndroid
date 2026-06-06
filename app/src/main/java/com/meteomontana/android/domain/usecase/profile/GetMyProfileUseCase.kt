package com.meteomontana.android.domain.usecase.profile

import com.meteomontana.android.data.api.ProfileApi
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.PrivateProfile
import javax.inject.Inject

class GetMyProfileUseCase @Inject constructor(private val api: ProfileApi) {
    suspend operator fun invoke(): PrivateProfile = api.getMyProfile().toDomain()
}
