package com.meteomontana.android.domain.usecase.profile

import com.meteomontana.android.domain.model.PrivateProfile
import com.meteomontana.android.domain.repository.ProfileRepository

class GetMyProfileUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(): PrivateProfile = repository.getMyProfile()
}
