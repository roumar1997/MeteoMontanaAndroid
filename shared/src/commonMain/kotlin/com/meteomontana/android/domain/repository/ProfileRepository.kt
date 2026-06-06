package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.PrivateProfile

interface ProfileRepository {
    suspend fun getMyProfile(): PrivateProfile
}
