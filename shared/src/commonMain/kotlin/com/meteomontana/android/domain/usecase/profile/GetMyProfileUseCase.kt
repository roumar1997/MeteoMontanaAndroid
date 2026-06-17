package com.meteomontana.android.domain.usecase.profile

import com.meteomontana.android.domain.model.PrivateProfile
import com.meteomontana.android.domain.repository.ProfileRepository

class GetMyProfileUseCase(private val repository: ProfileRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): PrivateProfile = repository.getMyProfile()
}

class UpdateMyProfileUseCase(private val repository: ProfileRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(req: com.meteomontana.android.data.api.dto.UpdateProfileRequest): PrivateProfile =
        repository.updateMyProfile(req)
}

class UpdateFcmTokenUseCase(private val repository: ProfileRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(req: com.meteomontana.android.data.api.dto.FcmTokenRequest) =
        repository.updateFcmToken(req)
}
