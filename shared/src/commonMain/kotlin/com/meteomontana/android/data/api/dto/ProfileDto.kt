package com.meteomontana.android.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class PrivateProfileDto(
    val uid: String,
    val email: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val bio: String? = null,
    val topGrade: String? = null,
    val isPublic: Boolean,
    val isAdmin: Boolean,
    val isPremium: Boolean,
    val gender: String? = null
)

@Serializable
data class UpdateProfileRequest(
    val username: String? = null,
    val displayName: String? = null,
    val bio: String? = null,
    val topGrade: String? = null,
    val isPublic: Boolean? = null,
    val photoUrl: String? = null,
    val gender: String? = null
)

@Serializable
data class FcmTokenRequest(val token: String)
