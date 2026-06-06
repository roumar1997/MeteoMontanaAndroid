package com.meteomontana.android.domain.usecase.profile

import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.dto.PrivateProfileDto
import javax.inject.Inject

class GetMyProfileUseCase @Inject constructor(private val api: SchoolApi) {
    suspend operator fun invoke(): PrivateProfileDto = api.getMyProfile()
}
